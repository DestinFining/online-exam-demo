# 在线刷题题量动态选择

## 功能

- 题目数量由固定档位滑块调整为数字步进选择器，可逐题调整。
- 新增 `GET /api/v1/practice/count`，根据学科、知识点、题型、难度筛选条件统计状态为启用的可自动判分题目数量。
- 章节刷题时，页面即时显示所选学科/知识点范围内可刷题总数，并将该数量作为本轮题数的最大值。筛选项变化时重新请求总数。
- 数量上限降低时，当前选择会自动收敛到新上限；无符合条件题目时禁用开始按钮并提示调整筛选条件。单轮练习最多支持 5,000 道，匹配题量更多时会同时显示实际题库总数和单轮安全上限。
- 简答题与编程题尚未接入自动判分，因此不计入可刷题总数；明确选择这两种题型时返回 0。
- 用请求序号忽略过期响应，避免快速更改筛选条件时旧的计数结果覆盖新结果。

## API

`GET /api/v1/practice/count?subjectId=1&knowledgePointId=3&type=SINGLE_CHOICE&difficulty=EASY`

响应 `data` 为数字，表示匹配的启用且支持自动判分的题目数。筛选参数均可省略。权限沿用 `practice:start`。

## 手工验收

1. 进入在线刷题，确认题目数量使用数字步进控件，并显示可用题数和最大值。
2. 切换到“章节刷题”，选择学科和知识点，确认总数跟随条件实时变化。
3. 选择题型或难度，确认总数同时变化。
4. 将筛选条件切换为较少题目的知识点，确认当前数量自动降低到新上限。
5. 选择没有题目的条件，确认数字控件和开始按钮被禁用。
6. 在后端确认主观题不会被计入可自动判分数量，避免计数与实际组题规则不一致。


### Null-filter bug fix

A regression fix guards `SUBJECTIVE_TYPES.contains(type)` when `type` is null. Java immutable sets created by `Set.of(...)` throw `NullPointerException` for `contains(null)`. Empty or null type/difficulty filters now continue as “no filter”, and the count query is still executed against enabled, auto-gradable questions. Unknown/null types in the evaluation path are rejected with the normal business error rather than causing an NPE.
