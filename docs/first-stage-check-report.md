# 第一阶段：工程稳定性检查报告

检查日期：2026-10-09
基线：题库批量操作 + 匿名阅卷 + 单场考试成绩批量发布完整工程。

## 已完成的静态检查

- `backend/pom.xml` XML 结构可解析。
- `frontend/package.json` JSON 结构可解析，定义了 `build` 脚本。
- 题库批量接口引用的 `BatchQuestionRequest` 与 `BatchDeleteQuestionsRequest` 文件存在。
- `GradeSummary` 记录定义与 `GradeService` 中 `new GradeSummary(...)` 调用的参数数量一致。
- `com.example.exam` 包内的 Java import 路径存在。
- 前端 `@/`、相对路径本地 import 可解析。
- ZIP 完整性将在交付打包后另行校验。

## 尚待在 Windows 开发机验证

生成环境无 Maven 可执行文件，且未安装 `frontend/node_modules`，因此未实际运行：

- `mvn -U clean test`
- `npm install`
- `npm run build`

不能把静态一致性检查当作编译通过。请在 Java 21、Maven 3.9+、Node.js 22.12+ 环境中执行 `scripts/verify-build.ps1`，并保存控制台输出。

## 重要：保持单一源码基线

之前的编译报错来自部分文件混合覆盖时的类型/DTO 不匹配。应使用此完整 ZIP 创建一个干净项目目录，并只把原项目的本地环境配置（例如 MySQL 地址和密码）按需迁移；不要再把早期版本中的 `QuestionController.java`、`GradeService.java`、`GradeSummary.java` 或 DTO 文件覆盖到此版本上。
