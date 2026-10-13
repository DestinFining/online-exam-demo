# Vue 3 前端

## 本地开发

```bash
npm install
npm run dev
```

需要后端运行在 `http://localhost:8080`。Vite 开发服务器会代理 `/api/*` 到后端。

## 类型检查与构建

```bash
npm run typecheck
npm run build
```

## 路由与角色

- `STUDENT`：工作台、刷题、错题、收藏、笔记、考试、成绩、学情分析
- `TEACHER`：工作台、题库、知识点、试卷、考试、阅卷与成绩
- `ADMIN`：工作台、题库、学科、知识点、试卷、考试、阅卷与成绩

前端路由守卫仅用于用户体验。数据安全最终由 Spring Security 与后端 API 权限校验保证。
