# 前后端接口对齐说明（Vue 3 前端 v1）

前端 API 封装统一位于 `frontend/src/api/modules.ts`，Base URL 默认 `/api/v1`，开发环境由 Vite 把 `/api` 代理到 `http://localhost:8080`。

## 已接入的后端接口

| 前端功能 | API 前缀 | 对应后端模块 |
|---|---|---|
| 登录/注册/当前用户 | `/auth` | `AuthController` |
| 学科 | `/subjects` | `SubjectController` |
| 知识点 | `/knowledge-points` | `KnowledgePointController` |
| 题库 | `/questions` | `QuestionController` |
| 刷题 | `/practice` | `PracticeController` |
| 错题本 | `/wrong-questions` | `WrongQuestionController` |
| 收藏 | `/favorites` | `FavoriteController` |
| 笔记 | `/question-notes` | `QuestionNoteController` |
| 试卷/动态组卷 | `/papers` | `PaperController` |
| 考试/保存/恢复/交卷/事件 | `/exams` | `ExamController` |
| 阅卷/成绩发布 | `/grades` | `GradeController` |
| 学生学习统计 | `/statistics/student` | `StatisticsController` |

## 实现边界

1. 学生刷题接口只返回题面和选项，当前 UI 展示服务端返回的判定信息，不会在前端推算或泄露标准答案。
2. 考试页面每 8 秒请求保存答案，同时使用浏览器本地缓存辅助恢复。服务端时间与考试状态才是最终依据。
3. 考试切屏、网络事件是可检测事件日志，不等于能够阻止所有外部作弊行为。
4. 后端目前没有用户/角色/权限 CRUD API，所以管理员入口不伪造完整的用户管理功能。
5. 后端目前没有题库 Excel/JSON 导入导出 API，本版不提供无效的上传/导出按钮。
6. 编程题仅保存代码文本，不执行代码；若需在线判题，需要另行实现隔离沙箱与资源限制。
7. 笔记 API 当前以题目 ID 查询，前端提供按题目 ID 打开笔记的入口。
