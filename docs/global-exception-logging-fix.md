# 全局异常堆栈日志修复

## 行为

- 未预期异常（HTTP 500）：使用 SLF4J `log.error(..., ex)` 记录 HTTP 方法、请求路径和完整异常堆栈。异常 cause 链会随 Throwable 一起进入日志。
- HTTP 响应仍只返回 `服务器内部错误`，不会返回 SQL、数据库驱动或内部异常消息。
- 参数校验与约束校验异常记录为 WARN；预期的 `BusinessException` 保留业务响应，不重复输出堆栈。
- 除控制台日志外，默认将应用日志写入 `backend/logs/online-exam.log`。可通过环境变量 `LOG_FILE` 更改路径。请限制日志文件访问权限，并避免把日志提交到公共代码仓库。

## 验证

项目包含 `GlobalExceptionHandlerTest.unexpectedErrorReturnsGenericMessageAndLogsStackTrace`，检查 500 响应不泄漏内部异常内容，并验证日志事件包含请求方法/路径和 Throwable（堆栈）。

在 `backend` 目录运行：

```powershell
mvn clean test
```

本次交付只完成源码、配置和测试断言检查；如未在目标机器运行上述命令，不应视为本机集成测试已通过。
