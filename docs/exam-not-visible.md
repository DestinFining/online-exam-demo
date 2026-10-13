# 教师发布试卷后学生在考试中心看不到

## 原因

本系统将“试卷”和“考试”分成两个业务对象：

1. 在“试卷管理”中创建、发布试卷，数据保存在 `paper` 表。
2. 在“考试管理”中选择已发布试卷创建考试，数据保存在 `exam` 表。
3. 新创建的考试初始状态为 `DRAFT`，教师需要再点击“发布考试”，将状态改为 `PUBLISHED`。
4. 学生考试中心调用 `GET /api/v1/exams`，仅查询 `status = 'PUBLISHED'` 的考试。

因此，仅发布试卷不会让它直接出现在学生考试中心。

## 当前前端的快捷流程

1. 教师进入“试卷管理”。
2. 发布试卷。
3. 点击该试卷对应的“安排考试”按钮。
4. 系统跳转到“考试管理”，并自动选中这份试卷、打开创建考试对话框。
5. 设置考试名称、开始时间、结束时间、时长及考生范围，创建考试。
6. 在考试列表中找到刚创建的草稿，点击“发布考试”。
7. 学生刷新“考试中心”即可看到已发布考试。

## 检查仍不可见时

在 MySQL 中执行：

```sql
SELECT id, name, paper_id, start_time, end_time, candidate_scope, status, creator_id
FROM exam
ORDER BY id DESC;
```

目标考试必须存在且 `status = 'PUBLISHED'`。关联试卷也必须已经发布：

```sql
SELECT e.id AS exam_id, e.name AS exam_name, e.status AS exam_status,
       p.id AS paper_id, p.name AS paper_name, p.status AS paper_status
FROM exam e
JOIN paper p ON p.id = e.paper_id
ORDER BY e.id DESC;
```

若 `candidate_scope = 'ASSIGNED'`，开始考试时还必须检查当前学生 ID 是否存在于 `exam_candidate` 表。若需要所有学生可参加，创建考试时应选择“所有有资格学生（ALL）”。
