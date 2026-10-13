# 前端 TypeScript 构建错误修复记录（2026-10-09）

修复了 `verify-build.ps1` 报告的三个 TypeScript 错误：

1. `DashboardView.vue`：为异构异步任务声明 `Promise<unknown>[]`，并把响应赋值改为块级回调，避免数组元素推断成多种不兼容的 Promise 类型。
2. `PracticeView.vue`：移除 `@element-plus/icons-vue` 不存在且未使用的 `Bookmark` 导入。
3. `QuestionBankView.vue`：传入 `removeOption` 前使用 `Number(index)`，显式将模板循环的 `string | number` 索引转换为数字。

## 在 Windows 验证

```powershell
cd frontend
npm ci
npm run build
```

如果该工程没有 `package-lock.json`，请使用 `npm install` 生成 lock 文件后再运行 `npm run build`。在本次生成环境中，依赖安装超时，因此未声称 `vue-tsc` / Vite 完整构建通过。
