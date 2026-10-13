# Redis 第一阶段缓存接入说明

## 缓存范围

| 数据 | 缓存策略 | TTL | 目的 |
|---|---|---:|---|
| 学科列表（按是否启用区分） | JSON 缓存 | 10 分钟 | 减少重复读取学科目录 |
| 学科详情 | JSON 缓存 | 10 分钟 | 减少目录详情读取 |
| 知识点列表（按学科、启用状态区分） | JSON 缓存 | 10 分钟 | 减少重复读取知识点树/列表 |
| 知识点详情 | JSON 缓存 | 10 分钟 | 减少目录详情读取 |
| 题库分页列表（按分页和筛选条件区分） | JSON 缓存 | 90 秒 | 缓存常用只读题目列表 |

暂不缓存题目详情/答案、答卷、成绩、个人学情等敏感或实时性要求更高的数据，避免权限数据混用和过期结果干扰考试。

## Key 与失效策略

- 缓存项使用 `online-exam:catalog:*` 前缀。
- 各目录分别维护 `online-exam:cache:version:subjects`、`...:knowledge-points`、`...:questions` 版本号。写入时增加对应版本；版本号写入在数据库事务提交后执行。
- 版本号变更后，新请求自然使用新版本 Key，避免使用旧列表；旧 Key 依靠 TTL 自动过期。
- 同一数据库事务内对同一缓存域的多次变更会合并为一次版本变更。
- 新增、编辑、停用/删除题目，以及 Excel 导入和批量操作经由 `QuestionService` 写入后都会触发题库缓存失效。

## Redis 不可用时的行为

Redis 是可选加速层，MySQL 仍为数据源。读取版本或缓存失败时会跳过缓存并查询 MySQL；写缓存和失效失败不会让业务请求失败。Redis 连接与命令超时配置为 500ms，缓存 TTL 限制了 Redis 短暂不可用期间的陈旧数据窗口。

## 验证

后端目录执行：

```powershell
mvn -Dtest=RedisJsonCacheTest,StatisticsServiceTest test
mvn clean test
```

可在 Redis CLI 检查：

```text
SCAN 0 MATCH online-exam:catalog:* COUNT 100
SCAN 0 MATCH online-exam:cache:version:* COUNT 100
TTL <具体缓存键>
```

为避免阻塞 Redis，使用 `SCAN` 而不是 `KEYS *`。可在调用学科、知识点、题库分页接口两次后检查缓存键；新增或编辑相应数据后，版本号应增加，下一次读请求应写入新版本的缓存键。
