# 核心自动化测试运行报告

## 最近一次结果（本机 Windows，JDK 21）

- **`mvn -B test`：BUILD SUCCESS，Tests run: 49, Failures: 0, Errors: 0, Skipped: 0**（约 15 s）。
- 测试类 11 个，全部是 JUnit 5 + Mockito 的服务层单元测试，不依赖 MySQL/Redis。
- 报告位置：`backend/target/surefire-reports/`（`*.txt` 为汇总，`TEST-*.xml` 为明细）。

## 逐类用例数

| 测试类 | 用例数 | 覆盖内容 |
|---|---:|---|
| AuthServiceTest | 6 | 注册唯一性校验、密码不一致拦截、停用账号与错误密码统一返回 401、登录失败计数与登出令牌吊销接入 |
| GlobalExceptionHandlerTest | 2 | 业务错误 HTTP 状态；未处理异常通用响应与堆栈日志 |
| RedisJsonCacheTest | 3 | 缓存读写、命名空间失效、Redis 异常时降级不抛错 |
| SeedQuestionDataConfigTest | 3 | 演示题库播种：无用户时跳过、已存在时跳过、正常写入 |
| ExamServiceTest | 8 | 草稿考试不可参加；重复交卷幂等；客观题即时判分、主观题待批改；已结束考试归档删除；未到结束时间禁止删除；**并发开考撞唯一键时复用已创建答卷／无可见答卷时返回 409** |
| GradeServiceTest | 3 | 未发布成绩不泄漏分数；无提交答卷时拒绝一键发布；**成绩发布后本人查看看不到主观题评分要点** |
| PaperServiceTest | 5 | 动态组卷题目不足返回 409 并给数量；组卷与编辑的分值/题量约束 |
| PracticeServiceTest | 9 | 默认刷题筛选、空题库、客观题判分、主观题入口拦截、主观题不误收录错题等 |
| LoginAttemptServiceTest | 4 | 15 分钟窗口内 5 次失败后锁定、成功登录清零、用户名大小写归并、Redis 故障时放行 |
| TokenRevocationServiceTest | 4 | 登出后 jti 进入黑名单、TTL 传递、未吊销令牌放行、Redis 故障时放行 |
| StatisticsServiceTest | 2 | 学生统计口径（正确率、平均分、最高分、无数据兜底） |
| **合计** | **49** | |

> 早期版本的本报告记录 12 项测试，且当时执行环境未安装 Maven，因此当时未把 JUnit 标记为通过。该缺口已在本机执行补齐。

## 在 Windows 开发机运行

```powershell
cd backend
mvn -B test                                   # 全量
mvn -B -Dtest=ExamServiceTest test            # 单个类
```

注意事项：

- Maven 的本地仓库位于工作区之外（本机为 `D:\java\apache-maven-3.3.9-bin\Resporty`）。若当前会话处于受限沙箱，Maven 写仓库锁文件会被拒绝并报
  `Could not open file channel for 'NamedLockKey{...}' after 5 attempts`；`-Daether.syncContext.named.factory=noop` 不能规避，需要在非受限模式下运行。
- MyBatis-Plus `BaseMapper` 的 `insert(T)` / `insert(Collection<T>)` 等重载会让 Mockito 的 `any()` 推断歧义，验证时需写成 `any(ExamAttempt.class)` 这类显式匹配器。
- MockitoExtension 默认 strict stubs：新增用例时，未被调用的 `when(...)` 会让整个类报 `UnnecessaryStubbingException`。

## 说明

本套测试使用 Mockito 隔离 MyBatis Mapper，属于服务层单元测试，不依赖正在运行的 MySQL/Redis，因此**不能**替代真实数据库集成测试或浏览器端到端测试。仍未覆盖的是：

- 需要真实数据库的链路：分页 SQL、唯一约束冲突、事务回滚边界、动态组卷的并发写入。
- 需要真实 Redis 的行为：登录锁定计数在真实 TTL 下的过期、令牌黑名单的过期回收。
- HTTP 层：Spring Security 过滤器链、`@PreAuthorize` 权限点、`/auth/logout` 的实际请求—响应。

这些应通过 `@SpringBootTest` / MockMvc 与容器化依赖继续补充。
