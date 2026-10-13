# 第五阶段冒烟测试

## 目标
验证“考试提交 → 自动评分 → 主观题阅卷 → 成绩结算 → 成绩发布 → 学生查询”的闭环。

## 测试顺序

1. 教师创建并发布包含至少一个客观题和一个简答题的试卷。
2. 教师创建并发布考试。
3. 学生开始考试，保存答案，提交试卷。
4. 客观题应立即得到 `autoScore`，存在主观题时答卷状态应为 `SUBMITTED`。
5. 教师调用 `GET /api/v1/grades/exam/{examId}` 查看提交列表。
6. 教师调用 `GET /api/v1/grades/{attemptId}/answers` 查看学生答案和参考答案。
7. 教师调用 `PUT /api/v1/grades/answers/{answerId}` 批改主观题。
8. 教师调用 `POST /api/v1/grades/{attemptId}/finalize`。
9. 答卷状态应变为 `GRADED`，`totalScore = objectiveScore + subjectiveScore`。
10. 教师调用 `POST /api/v1/grades/{attemptId}/publish`。
11. 学生调用 `GET /api/v1/grades/my` 查看成绩。
12. 学生调用 `GET /api/v1/grades/{attemptId}/answers` 查看最终答卷、解析和教师评语。
13. 查询 `wrong_question`，确认客观错题已经自动收录。

## 越权测试

- 学生不能访问 `GET /api/v1/grades/exam/{examId}`。
- 教师不能批改其他教师创建的考试。
- 学生不能访问未发布成绩的其他答卷。
- 客观题调用人工评分接口必须返回 409。
- 人工评分不得超过题目满分。
