# 一期接口测试

## 登录学生

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"student","password":"student123456"}'
```

从响应中的 `data.accessToken` 复制 Token。

## 查询当前用户

```bash
curl http://localhost:8080/api/v1/auth/me \
  -H 'Authorization: Bearer <TOKEN>'
```

## 学生 RBAC

```bash
curl http://localhost:8080/api/v1/demo/student \
  -H 'Authorization: Bearer <TOKEN>'
```

## 越权测试

学生访问管理员接口应得到 HTTP 403：

```bash
curl -i http://localhost:8080/api/v1/demo/admin \
  -H 'Authorization: Bearer <TOKEN>'
```

## 登录教师并创建题目

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"teacher","password":"teacher123456"}'
```

创建题目：

```bash
curl -X POST http://localhost:8080/api/v1/questions \
  -H 'Authorization: Bearer <TEACHER_TOKEN>' \
  -H 'Content-Type: application/json' \
  -d '{
    "subjectId": 2,
    "knowledgePointId": 1,
    "type": "SINGLE_CHOICE",
    "content": "Java中负责创建对象的关键字是什么？",
    "answerConfig": "{\"answer\":\"A\"}",
    "analysis": "new关键字用于创建对象实例。",
    "difficulty": "EASY",
    "difficultyValue": 0.2,
    "defaultScore": 2,
    "tags": "[\"Java\",\"基础\"]"
  }'
```

## 健康检查

```bash
curl http://localhost:8080/actuator/health
```
