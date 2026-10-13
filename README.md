# 线上题库与在线考试刷题系统（全栈开发版）

当前版本：全栈功能增强 + 安全加固 + 构建体积优化（用户管理、Excel 题库导入导出、教师统计分析、匿名阅卷与一键发布；密钥外部化、登出吊销、登录锁定；首屏 JS 减少约 70%）

## 项目结构

```text
online-exam-fullstack-v1/
├── backend/                 # Spring Boot 4 后端（Java 21）
│   ├── pom.xml
│   ├── docker-compose.yml
│   ├── src/main/resources/schema.sql   # 幂等建表 + 种子数据
│   ├── docs/                # 后端专项文档（测试报告、统计优化等）
│   └── logs/                # 运行日志
├── frontend/                # Vue 3 + Vite + TypeScript 前端
│   ├── package.json
│   ├── vite.config.ts       # 分包策略 + Element Plus 按需引入
│   └── src/
│       ├── plugins/echarts.ts          # ECharts 按需注册
│       ├── api/             # axios 封装（ApiError）与接口模块
│       └── views/           # 按路由懒加载的页面
├── docs/                    # 项目文档（基线、检查报告、手工测试清单、优化说明）
└── scripts/                 # verify-build.ps1 等校验脚本
```

## 环境要求

- Node.js 22.12+（Vite 8 所需环境）
- JDK 21
- Maven 3.9+
- Docker Desktop（可选，用于启动 MySQL 与 Redis）

## 启动步骤

### 1. 启动数据库与 Redis

```bash
cd backend
docker compose up -d
```

MySQL 监听 `3307`，Redis 监听 `6379`。`docker-compose.yml` 不再内置口令，启动前需提供 `DB_PASSWORD`（缺失会直接报错退出）。

### 2. 初始化数据库

开发环境首次运行时按后端 README 的说明使用 `backend/src/main/resources/schema.sql` 初始化 MySQL 数据库。该脚本已改为可重复执行的幂等脚本（`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`，不含任何 `DROP`），只负责建表与补齐种子数据，不会改写已存在表的结构；后续表结构演进请使用迁移工具（Flyway/Liquibase）。

### 3. 启动后端

先在 `backend/` 目录准备环境变量：`DB_PASSWORD` 与 `JWT_SECRET` **必填**，缺失时后端拒绝启动。本机开发可创建被 `.gitignore` 忽略的 `src/main/resources/application-local.yml`，然后执行：

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

`local` 会同时激活 `dev`，只有 `dev` 会播种演示账号：

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | `admin` | `admin123456` |
| 教师 | `teacher` | `teacher123456` |
| 学生 | `student` | `student123456` |

默认监听 `http://localhost:8080`。生产环境不要激活 `dev`，此时不会创建任何演示账号；上线前请轮换所有密钥与口令。

### 4. 启动前端

打开另一个终端：

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。Vite 会将 `/api` 与 `/actuator` 请求转发到 `http://localhost:8080`。

如果后端地址不同，可复制 `.env.example` 为 `.env.local` 并配置：

```env
VITE_API_BASE_URL=http://localhost:8080/api/v1
```

## 当前前端功能

- 登录、学生自助注册、演示账号快速登录（演示凭据仅在开发模式渲染）
- 按角色切换导航（学生 / 教师 / 管理员）和路由访问限制
- 学生工作台、在线刷题、错题记录、收藏夹、题目笔记、考试中心、成绩记录、学情分析
- 教师题库 CRUD、JSON 批量新增、勾选批量移除（逻辑停用）、学科知识点管理、固定试卷/动态组卷、考试创建与发布、匿名答卷人工阅卷、单人发布与本场一键批量发布成绩
- 管理员用户 CRUD（账号创建、资料/角色修改、停用账号；逻辑停用以保留历史成绩）、学科/知识点/题库/试卷/考试和阅卷管理入口
- 题库 `.xlsx` 模板下载、逐行导入（返回失败行与原因）、导出当前可用题目；支持六种题型，选项列 A-F
- 教师考试统计：成绩按每份答卷满分归一化为得分率后统计分布；试卷难度优先使用已发布成绩平均得分率，尚无成绩时按题目难度等级与分值加权估算
- 考试界面：剩余时间、本地作答缓存、周期性服务端保存、断线提示、切屏事件记录、主动/超时交卷；纯客观题交卷后自动判分并直接发布，含简答题/编程题则等待教师阅卷
- ECharts 学情雷达图与成绩趋势图
- 退出登录会调用后端吊销当前令牌，而不是只清本地会话

## 安全设计（本轮加固）

| 项 | 现状 |
|---|---|
| 数据库口令 | `DB_PASSWORD` 必填环境变量，`docker-compose.yml` 同样要求；源码中不再有明文口令 |
| JWT 密钥 | `JWT_SECRET` 必填环境变量，缺失即启动失败；本机开发值放在被忽略的 `application-local.yml`，**上线前必须轮换** |
| SQL 日志 | 从 `StdOutImpl` 改为 `Slf4jImpl`，SQL 不再无条件打到标准输出（SQL 可能包含答案） |
| 演示数据 | `SeedDataConfig` / `SeedQuestionDataConfig` 加 `@Profile("dev")`；生产不激活 `dev` 就不会创建演示账号与题库 |
| 停用账号 | JWT 过滤器校验 `UserDetails.isEnabled()`，管理员停用账号后其现有令牌立即失效 |
| 登出 / 吊销 | JWT 带 `jti`；`POST /api/v1/auth/logout` 把 `jti` 写入 Redis 黑名单直至原过期时间；前端退出登录先调后端再清 `sessionStorage` |
| 登录失败锁定 | 同一用户名 15 分钟内失败 5 次返回 429，成功登录清零；用户名按小写归并，避免大小写绕过 |
| Redis 故障 | 吊销校验与失败计数均**降级放行**并打 WARN，缓存故障不会让所有人无法登录 |
| 认证范围 | `permitAll` 收窄到 `/auth/login`、`/auth/register`、`/error`、`/actuator/health`；`/auth/me`、`/auth/logout` 需要认证 |
| 前端权限 | `/exams`、`/grades` 显式声明 `['STUDENT','TEACHER','ADMIN']`（学生与教师共享页面）；路由守卫不再因损坏的 profile 崩溃，权限不足会提示而非静默跳转 |
| 错误呈现 | 统一 `ApiError`（含 `code`/`status`/`url`），403 不再静默；同一错误不会重复弹提示 |

> 密钥与口令此前已出现在受版本控制的文件中，**建议视为已泄露并全部轮换**：数据库口令、`JWT_SECRET`，以及任何曾经复用过的演示口令。

## 性能与构建优化

首屏（登录后进入任意页面）载荷：

| 产物 | 优化前 | 优化后 |
|---|---:|---:|
| 首屏 JS | 2315.0 KB（单文件，gzip 769.5） | 663.9 KB（gzip 219.3） |
| 首屏 CSS | 396.4 KB（gzip 58.2） | 205.6 KB（gzip 31.5） |
| ECharts | 含在首屏内 | 540.4 KB，仅统计页按需加载 |

手段：路由级懒加载、第三方库分包（`manualChunks`）、Element Plus 按需引入（`unplugin-vue-components`）、ECharts 按需注册（`src/plugins/echarts.ts`）。新增组件/图表类型的维护红线见 `docs/frontend-bundle-optimization.md`。

后端同轮优化：

- 并发开考不再返回 500：撞 `uk_attempt_exam_user_no` 时复用已创建的答卷，否则返回 409。
- 两处 N+1 查询改为批量查询（考试取题、阅卷取题）。
- 学情统计聚合早已下推到数据库，详见 `backend/docs/statistics-n-plus-one-optimization.md`。

## 测试与验证

- 后端：`mvn -B test` → **49 个用例、11 个测试类全部通过**（含登录锁定、令牌吊销、并发开考、成绩脱敏等新增用例），明细见 `backend/docs/core-test-run-report.md`。
- 前端：`npx vue-tsc --noEmit` 与 `npm run build` 均通过。
- 一次性校验：`.\scripts\verify-build.ps1`（检查版本、源码引用一致性、后端测试、前端构建）。
- Maven 的本地仓库位于工作区之外，若终端处于受限沙箱，会因无法写仓库锁文件而报 `Could not open file channel for 'NamedLockKey{...}' after 5 attempts`，请在非受限终端运行。

## 当前版本已知边界

1. 用户删除采用逻辑停用，而非物理删除，避免破坏历史考试、答卷和成绩的关联数据；角色管理允许分配系统内置的 STUDENT、TEACHER、ADMIN 三类角色。
2. Excel 题库功能仅接受 `.xlsx`，单次最多导入 2000 条非空数据行，导出最多取 50000 道启用题目；模板中的学科 ID 和知识点 ID 必须与当前数据库对应。
3. 教师试卷难度是教学分析指标，不等同于经典测量理论中的正式项目难度分析。已有已发布成绩时按平均得分率估计；无成绩时按试卷题目等级和分值加权估算。
4. 刷题接口当前仅返回题干与选项，不返回标准答案/解析；默认刷题只包含单选、多选、判断、填空题，简答题/编程题以及未知题型不会被自动判错或计入错题本。题目解析（`analysis`）不是答案，认证用户查看题目详情时可见；选项正确性与答案配置仍按权限脱敏。
5. 编程题当前支持文本代码提交，尚未接入沙箱编译/执行器。
6. 断线期间数据只在本地缓存，必须恢复网络后才能提交给服务端。浏览器无法保证设备崩溃、存储清理或强制关闭等所有场景都可恢复。
7. 当前 JWT 存于 `sessionStorage` 以便配合现有 Bearer Token 后端做本地毕设联调；生产部署建议改为安全的 HttpOnly Cookie / 刷新令牌方案并进行 HTTPS、CSP 和 XSS 防护配置。当前没有刷新令牌，登出吊销依赖 Redis，Redis 不可用期间已吊销的令牌会被放行。
8. 登录锁定是 Redis 计数器（15 分钟窗口），多实例部署时共享同一 Redis 才具备全局效果；未接入验证码或告警。
9. 管理端列表默认只显示启用项，页面显式传 `enabledOnly=false` 才能看到并重新启用已停用的学科/知识点。

## 主要接口

本次功能复用现有用户/角色/题目/考试表及 `schema.sql` 中已有的权限定义，不新增业务数据表。

认证：

- `POST /api/v1/auth/login` / `POST /api/v1/auth/register`：公开接口；登录失败 5 次后返回 429。
- `POST /api/v1/auth/logout`：需认证，吊销当前令牌。
- `GET /api/v1/auth/me`：需认证，返回当前用户与角色。

管理与其他：

- `GET /api/v1/users`：用户分页、关键词、角色和状态过滤（`user:view`）。
- `POST /api/v1/users` / `PUT /api/v1/users/{id}`：创建和更新用户、重置密码、分配内置角色。
- `DELETE /api/v1/users/{id}`：逻辑停用用户，不能停用当前登录账号。
- `GET /api/v1/questions/excel/template`：下载 Excel 模板（`question:import`）。
- `POST /api/v1/questions/excel/import`：表单字段 `file` 上传 `.xlsx`，返回导入统计和失败行。
- `GET /api/v1/questions/excel/export`：按 `subjectId`、`knowledgePointId`、`type`、`difficulty`、`keyword` 可选过滤导出（`question:export`）。
- `GET /api/v1/statistics/teacher`：教师查看本人统计；管理员可传 `teacherId` 筛选单个教师，不传则汇总全部教师。

## 技术栈

- Vue 3 + TypeScript + Vite 8
- Vue Router 4 + Pinia
- Element Plus（`unplugin-vue-components` 按需引入）
- Axios
- ECharts（按需注册）
- Spring Boot + Spring Security + JWT + MyBatis-Plus
- MySQL + Redis

## 工程稳定基线与构建验证

建议以后以此完整工程目录作为唯一源码基线，不要继续从不同版本压缩包中混合复制单个 Java 文件。先阅读 `docs/stability-baseline.md`，并在 Windows PowerShell 中运行：

```powershell
.\scripts\verify-build.ps1
```

脚本将依次检查 Java/Maven/Node/npm 版本、项目内源码引用一致性、后端 `mvn clean test` 和前端 `npm run build`。构建失败时会停在失败环节，按第一条实际报错修复。

## 文档索引

- `docs/stability-baseline.md`：工程稳定基线说明。
- `docs/first-stage-check-report.md`：阶段性检查报告。
- `docs/manual-test-checklist.md`：手工回归清单。
- `docs/frontend-bundle-optimization.md`：前端打包优化与维护红线。
- `backend/docs/core-test-run-report.md`：后端测试逐类明细与运行说明。
- `backend/docs/statistics-n-plus-one-optimization.md`：学情统计聚合优化。
- `docs/question-bank-expansion/`：120 道扩充题库导入脚本与清单。
- `docs/` 下其余按缺陷整理的修复记录（考试入口、组卷冲突、成绩发布、题库导入等）。

## 题库扩充包（120道题）

扩充题库的可选导入脚本和题目清单位于 `docs/question-bank-expansion/`。执行前请先备份 `online_exam` 数据库，并按该目录 README 操作。脚本按学科/知识点名称映射 ID，检查重复题干后插入，不会重建或清空已有数据。
