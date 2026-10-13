# 阅卷与成绩 API

## 1. 查看某场考试的已提交答卷

```http
GET /api/v1/grades/exam/{examId}
Authorization: Bearer <teacherToken>
```

仅考试创建教师或管理员可访问。

## 2. 查看答卷详情

```http
GET /api/v1/grades/{attemptId}/answers
Authorization: Bearer <teacherToken>
```

教师可看到题目参考答案；学生只能在成绩已发布后查看自己的答卷。

## 3. 批改主观题

```http
PUT /api/v1/grades/answers/{answerId}
Authorization: Bearer <teacherToken>
Content-Type: application/json

{
  "score": 8,
  "comment": "答案完整，核心概念表述准确。"
}
```

要求：
- 题型必须为 `SHORT_ANSWER` 或 `PROGRAMMING`；
- 分数不能超过该题满分；
- 教师只能批改自己创建的考试，管理员可以批改全部考试。

## 4. 结算成绩

```http
POST /api/v1/grades/{attemptId}/finalize
Authorization: Bearer <teacherToken>
```

全部主观题批改完成后：

```text
objectiveScore + subjectiveScore = totalScore
status: GRADED
```

## 5. 发布成绩

```http
POST /api/v1/grades/{attemptId}/publish
Authorization: Bearer <teacherToken>
```

只有 `GRADED` 状态可以发布。

## 6. 学生查看已发布成绩

```http
GET /api/v1/grades/my
Authorization: Bearer <studentToken>
```

只返回该学生自己的 `PUBLISHED` 成绩。

## 7. 错题自动收录

学生提交考试后，单选、多选、判断、填空的错误答案会自动写入 `wrong_question`：

```text
user_id + question_id
    ↓
不存在 → 新建错题
已存在 → wrong_count + 1、mastery_level = 0
```
