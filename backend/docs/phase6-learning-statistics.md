# 第六阶段：学习闭环与学情分析

## 新增能力

- 收藏夹：添加、取消、分页查询、收藏状态查询
- 题目笔记：创建、查询、修改、删除，并校验用户归属
- 章节/随机刷题：按学科、知识点、题型、难度筛选并随机抽题
- 刷题判分：单选、多选、判断、填空自动判定
- 错题联动：刷题答错自动写入错题本，连续重做正确提升掌握度
- 学情分析：刷题量、正确率、错题量、收藏量、考试次数、平均分、最高分、知识点正确率、成绩趋势

## 接口

```text
POST /api/v1/practice/start
POST /api/v1/practice/answer
GET  /api/v1/favorites
GET  /api/v1/favorites/{questionId}
POST /api/v1/favorites/{questionId}
DELETE /api/v1/favorites/{questionId}
GET  /api/v1/question-notes/question/{questionId}
POST /api/v1/question-notes/question/{questionId}
PUT  /api/v1/question-notes/{id}
DELETE /api/v1/question-notes/{id}
GET  /api/v1/statistics/student
```

## 关键规则

1. 学生练习接口不返回正确答案和解析中的敏感答案配置。
2. 只有客观题在练习阶段自动判分；简答/编程题保留给后续人工或扩展服务。
3. 学生只能修改或删除自己的笔记。
4. 收藏记录使用 `(user_id, question_id)` 唯一约束避免重复收藏。
5. `practice_record` 为学习行为事实表，个人统计由事实数据聚合得出。
