# 简答题 / 编程题新增 400 修复说明

## 原因

后端 `QuestionService.validate()` 对所有非选择题都要求 `answerConfig` 非空；此前题库管理页仅为填空题提交 `answerConfig`，简答题和编程题未发送该字段，因此 POST `/api/v1/questions` 返回 400。

## 修复

- 题库管理表单新增“参考答案”以及可选的“评分要点”。
- 新建/编辑简答题和编程题时将其保存为合法 JSON：`{"referenceAnswer":"...","gradingRubric":"..."}`。
- 填空题仍采用 `{"answers":[...]}` 格式。
- 未填写参考答案时，前端显示可理解的中文提示，不发出无效请求。
- 编辑已有题目时，会读取上述 JSON 并回填表单。

## 应用

替换 `frontend/src/views/QuestionBankView.vue` 后重新运行 `npm run dev`。如要确认生产构建，请在已安装依赖的前端目录运行 `npm run build`。
