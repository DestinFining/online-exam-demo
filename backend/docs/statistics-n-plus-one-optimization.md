# 学情统计 N+1 查询优化

## 原有问题

原来的 `StatisticsService.student()` 把某个学生的全部 `practice_record` 加载到 JVM，再循环执行 `questionMapper.selectById()` 查题目，之后批量读取知识点。查询量随刷题记录数增长，几千条记录也会造成大量冗余网络往返和对象构建开销。

## 修改方案

1. `PracticeRecordMapper.selectSummaryByUserId()` 使用 `COUNT(*)` 与 `SUM(CASE ...)` 在数据库侧返回总刷题数、正确数和非正确数，不再加载全部刷题记录。
2. `PracticeRecordMapper.selectKnowledgePointStatsByUserId()` 使用 `practice_record`、`question`、`knowledge_point` 的 JOIN 和 `GROUP BY` 直接聚合每个知识点的练习数与正确数；SQL 按正确率升序排序并最多返回 12 个知识点。
3. `ExamAttemptMapper.selectPublishedScoreSummary()` 用 `COUNT/AVG/MAX` 返回已发布考试次数、平均分和最高分。
4. `ExamAttemptMapper.selectLatestPublishedScoreTrend()` 只查询最近 10 条已发布成绩，避免把全部历史答卷加载进内存。
5. 错题数与收藏数继续通过现有 `selectCount()` 获取，不加载实体列表。

## 兼容性与统计口径

- 外部 `StudentStatistics` 返回结构保持不变。
- 练习记录中 `is_correct = 1` 计为正确；其他值（包括 `0` 和 `NULL`）按原有实现计入非正确数量。
- 知识点正确率以数据库聚合结果计算；没有练习记录时返回空知识点列表，概览数字为零。
- 依赖现有 MySQL 表结构，不需要数据库迁移。建议保持已有 `practice_record(user_id, question_id)` 索引。

## 测试

新增 `StatisticsServiceTest`，验证聚合值映射、知识点正确率、无数据场景和服务层不再调用 `practiceMapper.selectList()` 加载全部记录。

在 `backend` 目录执行：

```powershell
mvn -Dtest=StatisticsServiceTest test
mvn clean test
```

本补丁生成环境未安装 Maven；静态源码检查通过不等同于已在实际数据库上执行 SQL。升级后请在本机运行 JUnit，并使用测试数据库验证 SQL 方言与返回映射。
