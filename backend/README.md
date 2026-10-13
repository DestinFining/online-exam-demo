# 线上题库与在线考试刷题系统 - Spring Boot 后端一期

技术基线：Java 21 + Spring Boot 4.1.1 + Spring Security 7 + MyBatis-Plus 3.5.17 + MySQL 8.4 + Redis 7.4 + JJWT 0.13.0。

## 已实现

- 用户注册
- 用户登录
- BCrypt 密码哈希
- JWT Access Token
- `/api/v1/auth/me` 当前用户资料
- RBAC：角色 + 权限
- 后端方法级权限校验
- MySQL 数据库结构
- Redis 连接配置
- 统一 API 响应
- 全局异常处理
- CORS
- 开发演示账号自动初始化（仅 `dev` profile）
- 登出与令牌吊销（Redis，故障时降级放行）
- 登录失败计数与临时锁定（15 分钟内 5 次）
- Actuator health

## 目录结构

```text
src/main/java/com/example/exam
├── auth
├── common
├── config
├── demo
├── security
├── subject
├── knowledge
├── question
├── paper
└── system
```

## 1. 启动 MySQL + Redis

```bash
docker compose up -d
```

如果使用本机已经安装好的 MySQL / Redis，也可以跳过这一步。

## 2. 配置数据库

`DB_PASSWORD` 与 `JWT_SECRET` **没有默认值，缺失时应用直接拒绝启动**：

```text
DB_PASSWORD     必填，MySQL 口令
JWT_SECRET      必填，Base64 编码的 HMAC 密钥（可用 openssl rand -base64 48 生成）
```

其余可用环境变量覆盖：

```text
DB_URL                   默认 jdbc:mysql://localhost:3307/online_exam（docker compose 映射 3307）
DB_USERNAME              默认 root
REDIS_HOST / REDIS_PORT / REDIS_PASSWORD
JWT_ACCESS_TOKEN_MINUTES 默认 120
CORS_ALLOWED_ORIGINS     默认 http://localhost:5173,http://127.0.0.1:5173
SERVER_PORT              默认 8080
LOG_FILE                 默认 logs/online-exam.log
```

本机开发：自行创建 `src/main/resources/application-local.yml`（已被 `.gitignore` 忽略）填写本地口令，然后

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

`local` 会同时激活 `dev`（见 `spring.profiles.group`），而 `dev` 是唯一会播种演示账号与演示题库的 profile。
`docker compose` 读取同目录 `.env`：复制 `backend/.env.example` 为 `backend/.env` 并填入 `DB_PASSWORD`。

## 3. 启动后端

```bash
mvn spring-boot:run
```

或：

```bash
mvn clean package
java -jar target/online-exam-server-0.0.1-SNAPSHOT.jar
```

## 4. 演示账号

仅在 `dev` profile（例如 `-Dspring-boot.run.profiles=local`）下，首次启动时自动创建：

| 角色 | 用户名 | 密码 |
|---|---|---|
| 管理员 | admin | admin123456 |
| 教师 | teacher | teacher123456 |
| 学生 | student | student123456 |

生产环境不要激活 `dev`；无 profile 启动时不会创建任何演示账号。

## 5. 登录

```http
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "username": "student",
  "password": "student123456"
}
```

返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 3,
    "username": "student",
    "nickname": "演示学生",
    "roles": ["STUDENT"],
    "accessToken": "...",
    "expiresIn": 7200
  },
  "timestamp": 0
}
```

## 6. Token 调用

```http
GET /api/v1/auth/me
Authorization: Bearer <accessToken>
```

## 7. RBAC 验证

学生 Token：

```http
GET /api/v1/demo/student
```

教师 Token：

```http
GET /api/v1/demo/teacher
```

管理员 Token：

```http
GET /api/v1/demo/admin
```

例如学生访问 `/api/v1/demo/admin` 应返回 403。

## 8. 健康检查

```text
GET http://localhost:8080/actuator/health
```

## 注意

`schema.sql` 是可重复执行的幂等初始化脚本（`CREATE TABLE IF NOT EXISTS` + `INSERT IGNORE`，不含任何 `DROP`），用于首次建库并补齐种子数据，不会修改已存在表的结构。Spring Boot 不会自动执行它：它只在 `docker compose` 首次创建数据卷时由 MySQL 入口脚本执行。表结构演进请使用迁移工具（Flyway/Liquibase）。

## 当前实现状态

本目录是持续迭代中的后端工程，当前已经落地认证、RBAC、题库、动态组卷、在线考试和阅卷成绩核心链路。

## 第五阶段：阅卷与成绩

已实现：
- 教师查看某场考试的已提交答卷
- 教师查看答卷详情与参考答案
- 简答题/编程题人工评分
- 主观题满分校验
- 成绩结算：客观题 + 主观题
- 成绩发布
- 学生查看已发布成绩与答卷
- 客观错题自动收录
- 教师/管理员考试归属权限校验

详见 `docs/grade-api.md` 和 `docs/phase5-smoke-test.md`。

# 第二期：题库、知识点、试卷与动态组卷

## 已实现

- 学科查询与管理员 CRUD
- 多级知识点树查询与管理员 CRUD
- 六类题型模型
- 题目选项独立建模
- 题目创建/修改/删除/分页查询
- 学生查看题面时自动隐藏正确答案配置
- 固定试卷创建/修改/发布
- 规则试卷创建
- `/api/v1/papers/generate` 动态组卷接口
- 组卷支持题型、数量、分值、难度比例、知识点范围
- 动态组卷题目快照
- 试卷选项快照
- 启动时生成演示题库

## 创建单选题

```http
POST /api/v1/questions
Authorization: Bearer <teacherToken>
Content-Type: application/json

{
  "subjectId": 2,
  "knowledgePointId": 2,
  "type": "SINGLE_CHOICE",
  "content": "Java中创建对象的关键字是？",
  "analysis": "new用于创建对象。",
  "difficulty": "EASY",
  "difficultyValue": 0.3,
  "defaultScore": 2,
  "tags": "[\"java\"]",
  "options": [
    {"optionKey":"A","optionContent":"new","sort":1,"correct":true},
    {"optionKey":"B","optionContent":"class","sort":2,"correct":false},
    {"optionKey":"C","optionContent":"this","sort":3,"correct":false},
    {"optionKey":"D","optionContent":"super","sort":4,"correct":false}
  ]
}
```

## 创建固定试卷

```http
POST /api/v1/papers
Authorization: Bearer <teacherToken>
Content-Type: application/json

{
  "name": "Java阶段测试卷A",
  "subjectId": 2,
  "type": "FIXED",
  "duration": 60,
  "questions": [
    {"questionId": 1, "score": 2},
    {"questionId": 2, "score": 2},
    {"questionId": 9, "score": 1}
  ]
}
```

## 动态组卷

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

注意：若演示题库不足以满足某个细粒度难度/知识点规则，接口会明确返回“题库数量不足”，不会生成不符合规则的试卷。

## 第三阶段：在线考试核心模块

已加入 `exam` 模块，覆盖：

- 创建/修改/发布考试
- ALL / ASSIGNED 考生范围
- 最大考试次数
- 开始考试与未完成考试恢复
- 服务端有效结束时间
- 分批保存答案
- 基础乐观版本控制
- 主动提交与超时自动提交
- 客观题自动评分：单选、多选、判断、填空
- 简答/编程题进入人工阅卷状态
- 页面切出/全屏退出/断网等考试行为记录
- 考生考试接口隐藏正确答案和解析

接口详见 `docs/exam-api.md`。

## V5 新增

- 学生章节/随机刷题 API
- 错题联动
- 收藏夹
- 题目笔记
- 学情分析与知识点正确率


## Redis 第一阶段缓存

学科、知识点和题库分页列表已接入可选 Redis JSON 缓存。缓存范围、TTL、版本化失效策略和验证方法见 [`docs/redis-caching.md`](docs/redis-caching.md)。Redis 不可用时会跳过缓存并回源 MySQL。
