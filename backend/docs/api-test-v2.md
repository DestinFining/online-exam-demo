# 第二期接口测试清单

## 1. 启动环境

```bash
docker compose up -d
```

启动后端：

```bash
mvn spring-boot:run
```

## 2. 获取教师 Token

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "teacher",
  "password": "teacher123456"
}
```

## 3. 查看学科

```http
GET /api/v1/subjects
Authorization: Bearer <teacherToken>
```

## 4. 查看知识点

```http
GET /api/v1/knowledge-points?subjectId=2
Authorization: Bearer <teacherToken>
```

## 5. 查看题库

```http
GET /api/v1/questions?subjectId=2
Authorization: Bearer <teacherToken>
```

## 6. 创建题目

使用 README 中的单选题请求。

## 7. 创建固定试卷

使用 README 中的 FIXED 示例。成功后状态为 DRAFT。

## 8. 发布固定试卷

```http
POST /api/v1/papers/{id}/publish
Authorization: Bearer <teacherToken>
```

## 9. 动态组卷

```http
POST /api/v1/papers/generate
Authorization: Bearer <teacherToken>
Content-Type: application/json

{
  "name": "Java随机测试卷",
  "subjectId": 2,
  "type": "RULE",
  "duration": 60,
  "ruleConfig": {
    "totalScore": 15,
    "types": [
      {"type":"SINGLE_CHOICE","count":4,"score":2},
      {"type":"TRUE_FALSE","count":2,"score":1},
      {"type":"MULTIPLE_CHOICE","count":1,"score":5}
    ],
    "difficulty": {
      "EASY": 0.3,
      "MEDIUM": 0.5,
      "HARD": 0.2
    },
    "knowledgePointIds": []
  }
}
```

## 10. 安全验证

学生登录后：

- `GET /api/v1/questions/{id}` 不应返回 `answerConfig`；
- 题目选项的 `correct` 应为 `null`；
- 草稿试卷不应对学生可见；
- 学生调用 `POST /api/v1/papers` 应返回 403。

## 11. 规则校验验证

可以故意制造以下错误：

1. 固定试卷重复 questionId；
2. 动态组卷重复 type；
3. difficulty 比例之和不等于 1；
4. 题库数量不足；
5. 规则计算出的题型总分与 totalScore 不一致。

接口应拒绝创建，而不是产生一个不符合规则的试卷。
