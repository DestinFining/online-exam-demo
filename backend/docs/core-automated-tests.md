# 核心业务自动化测试说明

当前共有 17 个 JUnit 5/Mockito 单元测试，测试默认不需要启动 MySQL 或 Redis。

## 覆盖范围

- `GlobalExceptionHandlerTest`
  - 业务异常保留业务 HTTP 状态码和可读提示。
  - 未处理异常返回通用 500 提示，不向客户端泄露内部细节；后端日志应包含请求方法、请求路径和异常堆栈。
- `PracticeServiceTest`
  - 空题型/难度条件可正常刷题，默认随机刷题排除暂不支持自动判分的主观题。
  - 显式选择简答题时返回清楚的 409 提示。
  - 没有候选题时返回明确的 404 业务错误。
  - 客观题正确答案会持久化为正确。
  - 简答题/编程题不会被静默记录为错题，而是明确提示需要教师阅卷。
  - 未知题型会明确拒绝自动判分，不会插入刷题记录或错题记录。
- `PaperServiceTest`
  - 动态组卷题库不足时返回 409，错误信息包含需求数量和可用数量。
- `ExamServiceTest`
  - 草稿考试不能开始。
  - 已提交答卷重复提交具有幂等行为，不重复保存答案。
  - 纯客观题试卷提交后自动判分并直接发布成绩。
  - 含简答题的试卷仍进入人工阅卷流程。
- `StatisticsServiceTest`
  - 学情概览通过 SQL 聚合查询读取练习数量、正确率和考试成绩概览，不加载全部刷题记录。
  - 知识点正确率通过 JOIN/GROUP BY 获取，空数据时返回零值和空列表。
- `GradeServiceTest`
  - 成绩发布前学生列表不泄露客观题、主观题和总分。
  - 没有已提交答卷时，一键批量发布返回明确的 409 业务错误。

## Windows 执行

在 `backend` 目录运行：

```powershell
mvn -Dtest=GlobalExceptionHandlerTest,PracticeServiceTest,PaperServiceTest,ExamServiceTest,GradeServiceTest,StatisticsServiceTest test
```

查看所有测试：

```powershell
mvn clean test
```

如果希望保留失败报告，检查 `backend/target/surefire-reports/` 下的 `.txt` 和 `.xml` 文件。

## 测试边界

这些是使用 Mockito 隔离 Mapper 的单元测试，不会覆盖真实 MySQL SQL 兼容性、事务回滚、Redis 网络故障或完整 HTTP 安全过滤链。上述场景仍需在有 MySQL/Redis 的环境中进行集成测试。
