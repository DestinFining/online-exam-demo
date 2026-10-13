"""Offline source-contract checks; does not replace Maven/JUnit execution."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "backend"
checks = []

def check(name: str, condition: bool, detail: str = "") -> None:
    checks.append((name, bool(condition), detail))

handler = (BACKEND / "src/main/java/com/example/exam/common/exception/GlobalExceptionHandler.java").read_text(encoding="utf-8")
practice = (BACKEND / "src/main/java/com/example/exam/practice/PracticeService.java").read_text(encoding="utf-8")
statistics = (BACKEND / "src/main/java/com/example/exam/statistics/StatisticsService.java").read_text(encoding="utf-8")
practice_mapper = (BACKEND / "src/main/java/com/example/exam/practice/PracticeRecordMapper.java").read_text(encoding="utf-8")
attempt_mapper = (BACKEND / "src/main/java/com/example/exam/exam/ExamAttemptMapper.java").read_text(encoding="utf-8")
check("unhandled errors log method + path + throwable",
      'log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);' in handler)
check("client receives generic internal error only",
      'ApiResponse.error(500, "服务器内部错误")' in handler and "ex.getMessage()" not in handler[handler.index("@ExceptionHandler(Exception.class)"):])
check("explicit subjective practice filter is rejected clearly",
      "主观题（简答题、编程题）暂不支持自动判分" in practice)
check("default practice excludes subjective question types",
      '.notIn(type==null,"type",List.of("SHORT_ANSWER","PROGRAMMING"))' in practice)

files = [
    "src/test/java/com/example/exam/common/exception/GlobalExceptionHandlerTest.java",
    "src/test/java/com/example/exam/practice/PracticeServiceTest.java",
    "src/test/java/com/example/exam/paper/PaperServiceTest.java",
    "src/test/java/com/example/exam/exam/ExamServiceTest.java",
    "src/test/java/com/example/exam/grade/GradeServiceTest.java",
    "src/test/java/com/example/exam/statistics/StatisticsServiceTest.java",
    "src/test/java/com/example/exam/config/RedisJsonCacheTest.java",
]
test_count = 0
for rel in files:
    path = BACKEND / rel
    check(f"test source exists: {rel}", path.is_file())
    if not path.is_file():
        continue
    text = path.read_text(encoding="utf-8")
    count = len(re.findall(r"@Test\b", text))
    test_count += count
    check(f"test methods found: {rel}", count > 0, f"{count} tests")
    check(f"braces balanced: {rel}", text.count("{") == text.count("}"))
check("statistics use SQL aggregates rather than loading all practice rows",
      "selectSummaryByUserId(userId)" in statistics and "selectKnowledgePointStatsByUserId(userId)" in statistics
      and "practiceMapper.selectList(" not in statistics)
check("knowledge metrics aggregate in SQL with JOIN and GROUP BY",
      "INNER JOIN question q" in practice_mapper and "LEFT JOIN knowledge_point kp" in practice_mapper
      and "GROUP BY q.knowledge_point_id, kp.name" in practice_mapper)
check("published exam overview uses COUNT/AVG/MAX and trend is limited to ten rows",
      all(token in attempt_mapper for token in ["COUNT(*) AS exam_count", "AVG(total_score)", "MAX(total_score)", "LIMIT 10"]))
paper_service = (BACKEND / "src/main/java/com/example/exam/paper/PaperService.java").read_text(encoding="utf-8")
paper_controller = (BACKEND / "src/main/java/com/example/exam/paper/PaperController.java").read_text(encoding="utf-8")
exam_service = (BACKEND / "src/main/java/com/example/exam/exam/ExamService.java").read_text(encoding="utf-8")
exam_controller = (BACKEND / "src/main/java/com/example/exam/exam/ExamController.java").read_text(encoding="utf-8")
papers_view = (ROOT / "frontend/src/views/PapersView.vue").read_text(encoding="utf-8")
exams_view = (ROOT / "frontend/src/views/ExamsView.vue").read_text(encoding="utf-8")
check("draft and published papers are editable with owner and exam-history guards",
      '!"DRAFT".equals(paper.getStatus()) && !"PUBLISHED".equals(paper.getStatus())' in paper_service and "assertPublishedPaperEditable(id)" in paper_service and "只能修改自己创建的试卷" in paper_service)
check("paper batch deletion protects exam-linked papers", "public int deleteBatch(List<Long> ids" in paper_service and 'new QueryWrapper<Exam>().in("paper_id", distinctIds)' in paper_service and '"/batch-delete"' in paper_controller)
check("exam delete archives only ended published exams", "public void deleteEnded" in exam_service and 'exam.setStatus("ARCHIVED")' in exam_service and '"/api/v1/exams"' in exam_controller)
check("exam editing is blocked after attempts or start", "scheduledButNotStarted" in exam_service and '该考试已有作答记录，不能再修改考试信息' in exam_service)
check("management screens expose paper batch delete and draft/published edit",
      "deleteSelected" in papers_view and "openEdit(scope.row)" in papers_view and "editingStatus.value = row.status" in papers_view)
check("exam screen exposes update and ended-exam archive action", "saveExam" in exams_view and "deleteEnded(scope.row)" in exams_view)

cache_helper = (BACKEND / "src/main/java/com/example/exam/config/RedisJsonCache.java").read_text(encoding="utf-8")
subject_service = (BACKEND / "src/main/java/com/example/exam/subject/SubjectService.java").read_text(encoding="utf-8")
knowledge_service = (BACKEND / "src/main/java/com/example/exam/knowledge/KnowledgePointService.java").read_text(encoding="utf-8")
question_service = (BACKEND / "src/main/java/com/example/exam/question/QuestionService.java").read_text(encoding="utf-8")
check("Redis JSON cache has TTL-ready get/set and safe DB fallback", all(token in cache_helper for token in ["StringRedisTemplate", "readValue", "writeValueAsString", "return -1L"]))
check("catalog lists use Redis with write invalidation", "cache.get(cacheKey, SUBJECT_LIST_TYPE)" in subject_service and "invalidateAfterCommit(CACHE_NAMESPACE)" in subject_service and "cache.get(cacheKey, KNOWLEDGE_LIST_TYPE)" in knowledge_service)
check("question page cache is versioned and invalidated on question mutations", "questionPageCacheKey" in question_service and 'cache.invalidateAfterCommit("questions")' in question_service)
practice_controller = (BACKEND / "src/main/java/com/example/exam/practice/PracticeController.java").read_text(encoding="utf-8")
practice_view = (ROOT / "frontend/src/views/PracticeView.vue").read_text(encoding="utf-8")
practice_api = (ROOT / "frontend/src/api/modules.ts").read_text(encoding="utf-8")
practice_request = (BACKEND / "src/main/java/com/example/exam/practice/PracticeStartRequest.java").read_text(encoding="utf-8")
check("practice count query filters enabled auto-gradable questions and guards null filter type", "public long countAvailable(" in practice and '"status",1' in practice and 'List.of("SHORT_ANSWER","PROGRAMMING")' in practice and 'type!=null&&SUBJECTIVE_TYPES.contains(type)' in practice)
check("practice count endpoint uses practice:start permission", '@GetMapping("/count")' in practice_controller and "hasAuthority(\'practice:start\')" in practice_controller and "service.countAvailable(" in practice_controller)
check("practice UI uses numeric input and dynamic maximum from matching available count", "el-input-number v-model=\"settings.count\"" in practice_view and ":max=\"maxSelectableCount\"" in practice_view and "题目数量最大值：{{ maxSelectableCount }} 道" in practice_view)
check("practice count UI and backend share the 5000-question session safety limit", "@Max(5000)" in practice_request and "Math.min(availableQuestionCount.value, 5000)" in practice_view and 'LIMIT 5000' in practice)
check("frontend calls the available practice count endpoint", "get<number>(\'/practice/count\'" in practice_api and "refreshAvailableQuestionCount" in practice_view)
check("29 JUnit test cases declared", test_count == 29, f"found {test_count}")

for name, ok, detail in checks:
    print(("PASS" if ok else "FAIL") + " - " + name + (f" ({detail})" if detail else ""))
print(f"\nOffline source checks: {sum(ok for _, ok, _ in checks)}/{len(checks)} passed")
sys.exit(0 if all(ok for _, ok, _ in checks) else 1)
