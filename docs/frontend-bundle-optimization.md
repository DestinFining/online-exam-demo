# 前端打包与首屏体积优化

本轮把首屏 JS 从 **2315 KB（单文件）压到约 664 KB**，其中 gzip 后约 **219 KB**；Element Plus 的样式从 354 KB 降到 168 KB。改动集中在 5 个文件，下面记录做法与维护红线。

## 一、四项优化

| # | 手段 | 位置 |
|---|---|---|
| 1 | 路由级懒加载：19 个视图改为动态 `import()`，布局保持同步加载 | `frontend/src/router/index.ts` |
| 2 | 第三方库分包（`manualChunks` 函数式）：`vendor-vue` / `vendor-element-plus` / `vendor-echarts` / `vendor` | `frontend/vite.config.ts` |
| 3 | Element Plus 按需引入：`unplugin-vue-components` + `ElementPlusResolver({ importStyle: 'css' })`，不再 `app.use(ElementPlus)`、不再引 `element-plus/dist/index.css` | `frontend/vite.config.ts`、`frontend/src/main.ts` |
| 4 | ECharts 按需注册：只注册雷达/折线/柱状 + Grid/Radar/Tooltip + Canvas 渲染器 | `frontend/src/plugins/echarts.ts` |

## 二、效果（`npm run build`，本次实测）

| 产物 | 优化前 | 优化后 |
|---|---:|---:|
| 入口 `index-*.js` | 2315.0 KB（全部代码） | 17.07 KB |
| `vendor-element-plus-*.js` | （含在入口内） | 398.57 KB |
| `vendor-*.js` / `vendor-vue-*.js` | — | 217.54 / 30.72 KB |
| **首屏 JS 合计** | **2315.0 KB（gzip 769.5）** | **663.9 KB（gzip 219.3）** |
| `vendor-element-plus-*.css` | 354.7 KB | 167.95 KB |
| **首屏 CSS 合计** | 396.4 KB（gzip 58.2） | 205.6 KB（gzip 31.5） |
| `vendor-echarts-*.js` | 含在首屏 | 540.43 KB（**仅统计页加载**） |

`dist/index.html` 的 `modulepreload` 只列出 `rolldown-runtime`、`vendor`、`vendor-vue`、`vendor-element-plus`，**不含 `vendor-echarts`**，可据此验证 ECharts 没有回到首屏。

## 三、维护红线（改错会静默掉样式或报运行时错）

1. **模板里新增 `<el-xxx>`**：无需任何操作，resolver 会自动引入组件与其样式。
2. **新增命令式 API**（`ElMessage` / `ElMessageBox` 之外，例如 `ElNotification`、`ElLoading.service`）：必须在 `frontend/src/main.ts` 补一行
   `import 'element-plus/es/components/<name>/style/css'`，否则该组件**没有样式**（构建不报错，页面才看得出）。
3. **新增图表类型**（如饼图、仪表盘）：在 `frontend/src/plugins/echarts.ts` 的 `echarts.use([...])` 中注册对应的 chart / component / renderer，否则运行时报
   `Component series.pie not exists`。
4. **不要再写** `import ElementPlus from 'element-plus'` 或 `app.use(ElementPlus)`，也不要把 `element-plus/dist/index.css` 加回来——那会把整个组件库重新打进首屏。
5. `v-loading` 指令由 `main.ts` 里的 `ElLoading` 插件提供（`.use(ElLoading)`），不是自动导入的，不要删。
6. `element-plus/theme-chalk/base.css` 提供 `--el-*` CSS 变量与重置样式，必须保留。
7. 新增第三方大库时，在 `vite.config.ts` 的 `manualChunks` 里给它单独一个 chunk，避免污染 `vendor`。

## 四、自查命令

```powershell
cd frontend
npm run build
# 1) 首屏是否误含 ECharts：应查不到 vendor-echarts
Select-String -Path dist/index.html -Pattern 'modulepreload'
# 2) 按需样式是否齐全（抽查若干选择器，均应有命中）
$css = Get-Content -Raw (Get-ChildItem dist/assets/vendor-element-plus-*.css).FullName
'.el-table','.el-dialog','.el-select-dropdown__item','.el-pagination','.el-date-editor','.el-loading-mask','.el-message-box__header' |
  ForEach-Object { "$_ -> " + [regex]::Matches($css, [regex]::Escape($_)).Count }
```

> 注意：`.el-option`、`.el-tooltip__popper` 这类选择器在 Element Plus 里本就不存在（下拉项用 `.el-select-dropdown__item`，浮层用 `.el-popper`），查不到它们不代表样式缺失。
