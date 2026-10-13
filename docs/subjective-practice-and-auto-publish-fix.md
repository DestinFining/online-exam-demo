# 主观题刷题判分与客观题成绩自动发布修复

## 修复内容

1. `PracticeService.evaluate()` 只允许单选、多选、判断、填空四类客观题自动判分。简答题/编程题以及未知题型会返回明确的 409 业务错误，不会继续写入刷题记录或错题本。刷题候选题查询在未选择题型时继续排除简答题和编程题。
2. `ExamService.submitInternal()` 对纯客观题试卷在提交、自动判分成功后直接将答卷状态设为 `PUBLISHED`，学生可在成绩记录中查看成绩。
3. 试卷含有简答题或编程题时，答卷状态仍为 `SUBMITTED`，继续等待教师完成主观题阅卷和发布。
4. 如果试卷出现当前系统不认识的题型，会明确拒绝安全判分，不会将未知题型默认为错误或自动发布成绩。
5. 更新考试提交完成提示，纯客观题时明确告知成绩已发布。

## 新增回归测试

- `PracticeServiceTest.unsupportedQuestionTypeDoesNotGetSilentlyRecordedAsWrong`：未知题型不生成刷题记录或错题记录。
- `ExamServiceTest.objectiveOnlyExamPublishesResultImmediatelyAfterSubmit`：纯客观题交卷后状态为 `PUBLISHED` 并返回自动评分。
- `ExamServiceTest.examWithSubjectiveQuestionStillRequiresManualGrading`：含简答题的试卷仍进入人工阅卷流程。

## 验收要点

- 单选/多选/判断/填空组成的考试：学生交卷后查看“成绩记录”，应立即显示状态“已发布”和总分。
- 含简答题或编程题的考试：学生交卷后成绩仍不可见，教师完成所有主观题评分后再结算、发布。
- 刷题列表不默认包含简答题和编程题；若手工请求这两类题型，后端返回清晰提示，不新增刷题记录或错题记录。
