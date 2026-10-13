# 工程稳定基线说明

## 本版本用途

本压缩包是题库批量操作、教师匿名阅卷与单场考试一键发布功能的统一源码基线。请以此目录中的完整源码为准，不要把此前不同版本中下载的单个文件交叉覆盖，否则可能出现 DTO 缺失、record 构造参数不匹配等编译错误。

## 环境

- JDK 21
- Maven 3.9+
- Node.js 22.12+
- npm 10+
- Docker Desktop（仅当 MySQL/Redis 通过 Docker 提供时需要）

## 本机验证顺序

1. 先在 `backend/` 执行 `mvn clean test`。
2. 后端测试通过后，在 `frontend/` 执行 `npm install` 和 `npm run build`。
3. 两项构建均通过后，再启动 MySQL/Redis、后端和前端做端到端测试。

仓库目前没有提交 `package-lock.json`，因此前端使用 `npm install`，而不是 `npm ci`。首次安装后建议把生成的 `package-lock.json` 纳入版本控制，以固定依赖解析结果。

## 本地配置注意事项

- 检查 `backend/src/main/resources/application.yml` 中 MySQL 地址、数据库名、用户名、密码与当前 Docker 端口映射一致。
- 不要把真实密码提交到 Git。
- 不要执行 `docker compose down -v` 作为普通重启操作，它可能删除数据库数据卷。
- `backend/src/main/resources/schema.sql` 是初始化结构的依据；已有数据库请先备份，再按迁移方式更新，不要盲目重新初始化。

## 当前验证边界

本源码包已通过压缩包完整性检查和项目内的静态一致性检查。但当前生成环境没有 Maven 可执行文件，也没有安装前端 npm 依赖，因此不能在此宣称 `mvn clean test` 或 `npm run build` 已通过。必须在开发机上执行上面的真实构建命令，并以本机 Maven/npm 输出为准。
