# 在线考试 API

基础地址：`http://localhost:8080/api/v1`

所有考试接口均需要登录后的 Bearer Token。

## 1. 创建考试（教师/管理员）

`POST /exams`

```json
{
  "name": "Java阶段测试",
  "paperId": 1,
  "startTime": "2026-10-10T09:00:00",
  "endTime": "2026-10-10T11:00:00",
  "duration": 60,
  "maxAttempts": 1,
  "candidateScope": "ALL",
  "candidateUserIds": []
}
```

`candidateScope` 支持 `ALL` / `ASSIGNED`。指定考生时必须提供 `candidateUserIds`。

## 2. 发布考试

`POST /exams/{examId}/publish`

关联试卷必须已经发布。

## 3. 学生开始考试

`POST /exams/{examId}/start`

服务端检查：考试状态、考试时间窗口、指定考生资格、最大考试次数。已存在未提交记录时直接恢复该记录。

## 4. 自动保存

`POST /exams/{examId}/save`

```json
{
  "attemptId": 1,
  "clientVersion": 0,
  "answers": [
    {
      "paperQuestionId": 101,
      "answerContent": "A"
    },
    {
      "paperQuestionId": 102,
      "answerContent": ["A", "C"]
    }
  ]
}
```

`clientVersion` 用于基础乐观并发控制。成功保存后版本号递增。

## 5. 恢复考试

`GET /exams/{examId}/attempt`

返回服务器最近保存的答案和服务端计算的 `effectiveEndTime`。

## 6. 提交试卷

`POST /exams/{examId}/submit?attemptId=1`

请求体可传本次尚未保存的最后一批答案数组：

```json
[
  {
    "paperQuestionId": 101,
    "answerContent": "A"
  }
]
```

提交时服务端重新检查考试时间、答卷归属和答题状态，并执行客观题自动评分。存在简答/编程题时状态为 `SUBMITTED`，等待后续教师人工阅卷；全客观题试卷可直接进入 `GRADED`。

## 7. 考试行为事件

`POST /exams/{examId}/events`

```json
{
  "attemptId": 1,
  "eventType": "PAGE_HIDDEN",
  "clientInfo": "Chrome/154",
  "metadata": {
    "duration": 8
  }
}
```

当前支持页面切出、返回、全屏退出、网络离线/恢复、提交点击、自动提交、答案保存等事件。页面切出和全屏退出会累计 `cheatCount`。

## 安全约束

考生接口不会返回正确答案、答案配置、选项正确标识或题目解析；正式试卷使用服务端快照，前端修改考试时间或题目 ID 不能绕过后端校验。
