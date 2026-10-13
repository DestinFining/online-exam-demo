# 在线考试无法进入：修复说明

本次修复三类问题：

1. 学生考试列表只展示已发布且对当前学生开放的考试：`candidate_scope = ALL`，或考试 ID 存在于该学生的 `exam_candidate` 记录中。
2. 考试列表显示开放状态。考试未开始时显示“尚未开始”并展示预计开始时间；过期时显示“已截止”；只有处于开放时间窗口的考试显示“进入考试”。
3. 点击进入失败时不再吞掉 403 等权限异常；考试页面只在“没有正在进行的考试”这一特定 404 情形下尝试创建新考试记录，不再把所有异常一概作为启动重试。
4. 新建考试默认开始时间设为当前分钟、结束时间设为两小时后，便于本地演示；教师仍可修改为正式考试安排。

## 覆盖文件

- `backend/src/main/java/com/example/exam/exam/ExamService.java`
- `frontend/src/views/ExamsView.vue`
- `frontend/src/views/ExamTakeView.vue`

## 应用

在 `backend` 目录执行：

```powershell
mvn clean compile
mvn spring-boot:run
```

在 `frontend` 目录执行：

```powershell
npm run dev
```

## 检查当前考试数据

```sql
SELECT id, name, status, start_time, end_time, candidate_scope
FROM exam
ORDER BY id DESC;
```

如果考试状态不是 `PUBLISHED`，需要教师发布考试；如果开始时间晚于当前时间，需等到开始时间；如果结束时间已过，考试不能进入。对于 `ASSIGNED` 考试，还要检查该学生是否存在对应的 `exam_candidate` 记录。
