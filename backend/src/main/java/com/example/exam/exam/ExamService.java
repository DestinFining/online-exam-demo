package com.example.exam.exam;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.paper.Paper;
import com.example.exam.paper.PaperMapper;
import com.example.exam.paper.PaperQuestion;
import com.example.exam.paper.PaperQuestionMapper;
import com.example.exam.paper.PaperQuestionOption;
import com.example.exam.paper.PaperQuestionOptionMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.wrongquestion.WrongQuestion;
import com.example.exam.wrongquestion.WrongQuestionMapper;
import com.example.exam.system.user.SysUserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExamService {
    private static final Set<String> EVENT_TYPES = Set.of(
            "PAGE_HIDDEN", "PAGE_VISIBLE", "FULLSCREEN_EXIT", "NETWORK_OFFLINE", "NETWORK_ONLINE", "SUBMIT_CLICK", "AUTO_SUBMIT", "ANSWER_SAVE");
    private static final Set<String> OBJECTIVE_TYPES = Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK");
    private static final Set<String> SUBJECTIVE_TYPES = Set.of("SHORT_ANSWER", "PROGRAMMING");
    private static final Set<String> CHEAT_EVENTS = Set.of("PAGE_HIDDEN", "FULLSCREEN_EXIT");

    private final ExamMapper examMapper;
    private final ExamAttemptMapper attemptMapper;
    private final AnswerMapper answerMapper;
    private final ExamEventMapper eventMapper;
    private final ExamCandidateMapper candidateMapper;
    private final PaperMapper paperMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final PaperQuestionOptionMapper paperQuestionOptionMapper;
    private final SysUserMapper userMapper;
    private final WrongQuestionMapper wrongQuestionMapper;
    private final ObjectMapper objectMapper;

    public ExamService(ExamMapper examMapper,
                       ExamAttemptMapper attemptMapper,
                       AnswerMapper answerMapper,
                       ExamEventMapper eventMapper,
                       ExamCandidateMapper candidateMapper,
                       PaperMapper paperMapper,
                       PaperQuestionMapper paperQuestionMapper,
                       PaperQuestionOptionMapper paperQuestionOptionMapper,
                       SysUserMapper userMapper,
                       WrongQuestionMapper wrongQuestionMapper,
                       ObjectMapper objectMapper) {
        this.examMapper = examMapper;
        this.attemptMapper = attemptMapper;
        this.answerMapper = answerMapper;
        this.eventMapper = eventMapper;
        this.candidateMapper = candidateMapper;
        this.paperMapper = paperMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.paperQuestionOptionMapper = paperQuestionOptionMapper;
        this.userMapper = userMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
        this.objectMapper = objectMapper;
    }

    public Page<Exam> page(int page, int size, String status, String username, boolean teacherOrAdmin) {
        Page<Exam> result = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        QueryWrapper<Exam> query = new QueryWrapper<>();
        if (teacherOrAdmin) {
            SysUser user = findUser(username);
            if (!hasRole(user.getId(), "ADMIN") && !hasRole(user.getId(), "TEACHER")) {
                throw new BusinessException(403, "无权查看考试列表");
            }
            if (!hasRole(user.getId(), "ADMIN")) query.eq("creator_id", user.getId());
            query.eq(status != null && !status.isBlank(), "status", upper(status));
        } else {
            // Students can only see published exams and exams assigned to them.
            // Without this filter, the exam center lists ASSIGNED exams that the
            // current student cannot start, which looks like a broken enter button.
            SysUser student = findUser(username);
            query.eq("status", "PUBLISHED")
                    .and(q -> q.eq("candidate_scope", "ALL")
                            .or()
                            .inSql("id", "SELECT exam_id FROM exam_candidate WHERE user_id = " + student.getId()));
        }
        query.orderByDesc("start_time").orderByDesc("id");
        return examMapper.selectPage(result, query);
    }

    @Transactional
    public Exam create(ExamRequest request, String username, boolean admin) {
        validateExamRequest(request);
        Paper paper = requirePublishedPaper(request.paperId());
        SysUser creator = findUser(username);
        if (!admin && !hasRole(creator.getId(), "TEACHER")) throw new BusinessException(403, "仅教师或管理员可以创建考试");

        Exam exam = new Exam();
        exam.setName(request.name().trim());
        exam.setPaperId(paper.getId());
        exam.setStartTime(request.startTime());
        exam.setEndTime(request.endTime());
        exam.setDuration(request.duration());
        exam.setMaxAttempts(request.maxAttempts());
        exam.setCandidateScope(upper(request.candidateScope()));
        exam.setStatus("DRAFT");
        exam.setCreatorId(creator.getId());
        examMapper.insert(exam);

        if ("ASSIGNED".equals(exam.getCandidateScope())) {
            for (Long userId : request.candidateUserIds()) {
                if (userMapper.selectById(userId) == null) throw new BusinessException(400, "指定考生不存在: " + userId);
                ExamCandidate candidate = new ExamCandidate();
                candidate.setExamId(exam.getId());
                candidate.setUserId(userId);
                candidateMapper.insert(candidate);
            }
        }
        return exam;
    }

    @Transactional
    public void update(Long id, ExamRequest request, String username, boolean admin) {
        validateExamRequest(request);
        Exam exam = get(id);
        SysUser operator = findUser(username);
        if (!admin && !Objects.equals(exam.getCreatorId(), operator.getId())) throw new BusinessException(403, "只能修改自己创建的考试");
        boolean draft = "DRAFT".equals(exam.getStatus());
        boolean scheduledButNotStarted = "PUBLISHED".equals(exam.getStatus())
                && exam.getStartTime() != null && LocalDateTime.now().isBefore(exam.getStartTime());
        if (!draft && !scheduledButNotStarted) {
            throw new BusinessException(409, "只有草稿或尚未开始的已发布考试可以修改");
        }
        if (scheduledButNotStarted) {
            long attempts = attemptMapper.selectCount(new QueryWrapper<ExamAttempt>().eq("exam_id", id));
            if (attempts > 0) throw new BusinessException(409, "该考试已有作答记录，不能再修改考试信息");
            if (!request.startTime().isAfter(LocalDateTime.now())) {
                throw new BusinessException(400, "已发布考试的新开始时间必须晚于当前时间");
            }
        }
        requirePublishedPaper(request.paperId());

        exam.setName(request.name().trim());
        exam.setPaperId(request.paperId());
        exam.setStartTime(request.startTime());
        exam.setEndTime(request.endTime());
        exam.setDuration(request.duration());
        exam.setMaxAttempts(request.maxAttempts());
        exam.setCandidateScope(upper(request.candidateScope()));
        examMapper.updateById(exam);

        candidateMapper.delete(new QueryWrapper<ExamCandidate>().eq("exam_id", id));
        if ("ASSIGNED".equals(exam.getCandidateScope())) {
            for (Long userId : request.candidateUserIds()) {
                if (userMapper.selectById(userId) == null) throw new BusinessException(400, "指定考生不存在: " + userId);
                ExamCandidate candidate = new ExamCandidate();
                candidate.setExamId(id);
                candidate.setUserId(userId);
                candidateMapper.insert(candidate);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Long> candidateIds(Long id, String username, boolean admin) {
        Exam exam = get(id);
        SysUser operator = findUser(username);
        if (!admin && !Objects.equals(exam.getCreatorId(), operator.getId())) {
            throw new BusinessException(403, "只能查看自己创建的考试考生范围");
        }
        boolean draft = "DRAFT".equals(exam.getStatus());
        boolean scheduledButNotStarted = "PUBLISHED".equals(exam.getStatus())
                && exam.getStartTime() != null && LocalDateTime.now().isBefore(exam.getStartTime());
        if (!draft && !scheduledButNotStarted) throw new BusinessException(409, "只有草稿或尚未开始的已发布考试可以编辑");
        if (scheduledButNotStarted && attemptMapper.selectCount(new QueryWrapper<ExamAttempt>().eq("exam_id", id)) > 0) {
            throw new BusinessException(409, "该考试已有作答记录，不能再修改考试信息");
        }
        return candidateMapper.selectList(new QueryWrapper<ExamCandidate>().eq("exam_id", id))
                .stream().map(ExamCandidate::getUserId).toList();
    }

    @Transactional
    public void deleteEnded(Long id, String username, boolean admin) {
        Exam exam = get(id);
        SysUser operator = findUser(username);
        if (!admin && !Objects.equals(exam.getCreatorId(), operator.getId())) {
            throw new BusinessException(403, "只能删除自己创建的考试");
        }
        if (!Set.of("PUBLISHED", "ENDED").contains(upper(exam.getStatus()))
                || exam.getEndTime() == null
                || exam.getEndTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(409, "只能删除考试时间已结束的已发布考试");
        }
        // 采用归档式删除，保留答卷、成绩、阅卷记录和考试事件，避免破坏历史数据。
        exam.setStatus("ARCHIVED");
        examMapper.updateById(exam);
    }

    @Transactional
    public void publish(Long id, String username, boolean admin) {
        Exam exam = get(id);
        if (!"DRAFT".equals(exam.getStatus())) throw new BusinessException(409, "只有草稿考试可以发布");
        SysUser operator = findUser(username);
        if (!admin && !Objects.equals(exam.getCreatorId(), operator.getId())) throw new BusinessException(403, "只能发布自己创建的考试");
        Paper paper = paperMapper.selectById(exam.getPaperId());
        if (paper == null || !"PUBLISHED".equals(paper.getStatus())) throw new BusinessException(409, "关联试卷必须先发布");
        if (!exam.getEndTime().isAfter(exam.getStartTime())) throw new BusinessException(400, "考试结束时间必须晚于开始时间");
        exam.setStatus("PUBLISHED");
        examMapper.updateById(exam);
    }

    public Exam get(Long id) {
        Exam exam = examMapper.selectById(id);
        if (exam == null) throw new BusinessException(404, "考试不存在");
        return exam;
    }

    public ExamResponse overview(Long id) {
        Exam exam = get(id);
        Paper paper = paperMapper.selectById(exam.getPaperId());
        if (paper == null) throw new BusinessException(404, "关联试卷不存在");
        int questionCount = Math.toIntExact(paperQuestionMapper.selectCount(
                new QueryWrapper<PaperQuestion>().eq("paper_id", paper.getId())));
        return new ExamResponse(exam, paper.getName(), paper.getTotalScore(), questionCount);
    }

    @Transactional
    public ExamResponse.AttemptResponse start(Long examId, String username) {
        Exam exam = get(examId);
        SysUser student = findUser(username);
        ensureStudentAccess(exam, student.getId());

        LocalDateTime now = LocalDateTime.now();
        if (!"PUBLISHED".equals(exam.getStatus())) throw new BusinessException(409, "考试当前不可参加");
        if (now.isBefore(exam.getStartTime())) throw new BusinessException(409, "考试尚未开始");
        if (!now.isBefore(exam.getEndTime())) {
            exam.setStatus("ENDED");
            examMapper.updateById(exam);
            throw new BusinessException(409, "考试已经结束");
        }

        ExamAttempt current = attemptMapper.selectOne(new QueryWrapper<ExamAttempt>()
                .eq("exam_id", examId).eq("user_id", student.getId()).eq("status", "IN_PROGRESS")
                .orderByDesc("attempt_no").last("LIMIT 1"));
        if (current != null) return buildAttemptResponse(exam, current);

        long count = attemptMapper.selectCount(new QueryWrapper<ExamAttempt>().eq("exam_id", examId).eq("user_id", student.getId()));
        if (count >= exam.getMaxAttempts()) throw new BusinessException(409, "已达到该考试最大次数");
        int attemptNo = (int) count + 1;

        ExamAttempt attempt = new ExamAttempt();
        attempt.setExamId(examId);
        attempt.setUserId(student.getId());
        attempt.setAttemptNo(attemptNo);
        attempt.setStartTime(now);
        attempt.setStatus("IN_PROGRESS");
        attempt.setObjectiveScore(BigDecimal.ZERO);
        attempt.setSubjectiveScore(BigDecimal.ZERO);
        attempt.setTotalScore(BigDecimal.ZERO);
        attempt.setCheatCount(0);
        attempt.setVersionNo(0L);
        try {
            attemptMapper.insert(attempt);
        } catch (DuplicateKeyException concurrentStart) {
            // Two tabs (or two devices) can pass the IN_PROGRESS check above at the same
            // time; uk_attempt_exam_user_no then rejects the second insert. Reuse the
            // attempt the other request created instead of answering with a 500.
            ExamAttempt created = attemptMapper.selectOne(new QueryWrapper<ExamAttempt>()
                    .eq("exam_id", examId).eq("user_id", student.getId()).eq("status", "IN_PROGRESS")
                    .orderByDesc("attempt_no").last("LIMIT 1"));
            if (created != null) return buildAttemptResponse(exam, created);
            throw new BusinessException(409, "考试开始请求冲突，请刷新后重试");
        }

        return buildAttemptResponse(exam, attempt);
    }

    @Transactional
    public ExamAttemptDetail resume(Long examId, String username) {
        Exam exam = get(examId);
        SysUser user = findUser(username);
        ExamAttempt attempt = attemptMapper.selectOne(new QueryWrapper<ExamAttempt>()
                .eq("exam_id", examId).eq("user_id", user.getId()).eq("status", "IN_PROGRESS")
                .orderByDesc("attempt_no").last("LIMIT 1"));
        if (attempt == null) throw new BusinessException(404, "没有正在进行的考试");
        if (LocalDateTime.now().isAfter(effectiveEnd(exam, attempt))) {
            submitInternal(exam, attempt, List.of(), true);
        }
        return buildAttemptDetail(exam, attempt);
    }

    @Transactional
    public ExamAttemptDetail save(Long examId, SaveAnswerRequest request, String username) {
        Exam exam = get(examId);
        SysUser user = findUser(username);
        ExamAttempt attempt = requireOwnedAttempt(examId, request.attemptId(), user.getId());
        if ("SUBMITTED".equals(attempt.getStatus()) || "GRADED".equals(attempt.getStatus()) || "PUBLISHED".equals(attempt.getStatus())) {
            throw new BusinessException(409, "考试已经提交，不能继续保存答案");
        }
        long currentVersion = attempt.getVersionNo() == null ? 0L : attempt.getVersionNo();
        if (request.clientVersion() != null && !Objects.equals(request.clientVersion(), currentVersion)) {
            throw new BusinessException(409, "作答版本已变化，请刷新后再保存");
        }
        saveAnswers(exam, attempt, request.answers());
        LocalDateTime now = LocalDateTime.now();
        attempt.setLastSavedAt(now);
        attempt.setVersionNo(currentVersion + 1L);
        if (!now.isBefore(effectiveEnd(exam, attempt))) {
            submitInternal(exam, attempt, List.of(), true);
        } else {
            int updated = attemptMapper.update(attempt, new UpdateWrapper<ExamAttempt>()
                    .eq("id", attempt.getId()).eq("version_no", currentVersion).eq("status", "IN_PROGRESS"));
            if (updated != 1) throw new BusinessException(409, "作答保存冲突，请刷新后重试");
        }
        return buildAttemptDetail(exam, attempt);
    }

    @Transactional
    public SubmitResponse submit(Long examId, Long attemptId, List<SaveAnswerRequest.AnswerItem> answers, String username) {
        Exam exam = get(examId);
        SysUser user = findUser(username);
        ExamAttempt attempt = requireOwnedAttempt(examId, attemptId, user.getId());
        if ("SUBMITTED".equals(attempt.getStatus()) || "GRADED".equals(attempt.getStatus()) || "PUBLISHED".equals(attempt.getStatus())) {
            return submitResponse(attempt);
        }
        saveAnswers(exam, attempt, answers == null ? List.of() : answers);
        boolean timeout = !LocalDateTime.now().isBefore(effectiveEnd(exam, attempt));
        submitInternal(exam, attempt, List.of(), timeout);
        return submitResponse(attempt);
    }

    @Transactional
    public void recordEvent(Long examId, ExamEventRequest request, String username) {
        Exam exam = get(examId);
        SysUser user = findUser(username);
        ExamAttempt attempt = requireOwnedAttempt(examId, request.attemptId(), user.getId());
        String type = upper(request.eventType());
        if (!EVENT_TYPES.contains(type)) throw new BusinessException(400, "不支持的考试事件类型");
        if ("SUBMITTED".equals(attempt.getStatus()) || "GRADED".equals(attempt.getStatus()) || "PUBLISHED".equals(attempt.getStatus())) {
            throw new BusinessException(409, "考试已经结束");
        }
        ExamEvent event = new ExamEvent();
        event.setAttemptId(attempt.getId());
        event.setUserId(user.getId());
        event.setEventType(type);
        event.setEventTime(LocalDateTime.now());
        event.setClientInfo(request.clientInfo());
        event.setMetadata(toJson(request.metadata()));
        eventMapper.insert(event);
        if (CHEAT_EVENTS.contains(type)) {
            attempt.setCheatCount((attempt.getCheatCount() == null ? 0 : attempt.getCheatCount()) + 1);
            attemptMapper.updateById(attempt);
        }
    }

    private void validateExamRequest(ExamRequest request) {
        String scope = upper(request.candidateScope());
        if (!Set.of("ALL", "ASSIGNED").contains(scope)) throw new BusinessException(400, "考生范围只能是ALL或ASSIGNED");
        if (!request.endTime().isAfter(request.startTime())) throw new BusinessException(400, "考试结束时间必须晚于开始时间");
        if ("ASSIGNED".equals(scope) && (request.candidateUserIds() == null || request.candidateUserIds().isEmpty())) {
            throw new BusinessException(400, "指定考生模式必须提供考生ID列表");
        }
        if ("ALL".equals(scope) && request.candidateUserIds() != null && !request.candidateUserIds().isEmpty()) {
            throw new BusinessException(400, "全部考生模式不能提供指定考生列表");
        }
    }

    private Paper requirePublishedPaper(Long paperId) {
        Paper paper = paperMapper.selectById(paperId);
        if (paper == null) throw new BusinessException(404, "试卷不存在");
        if (!"PUBLISHED".equals(paper.getStatus())) throw new BusinessException(409, "只能将已发布试卷用于考试");
        return paper;
    }

    private void ensureStudentAccess(Exam exam, Long userId) {
        if ("ASSIGNED".equals(exam.getCandidateScope())) {
            long count = candidateMapper.selectCount(new QueryWrapper<ExamCandidate>()
                    .eq("exam_id", exam.getId()).eq("user_id", userId));
            if (count == 0) throw new BusinessException(403, "当前用户不在考试指定考生范围内");
        }
    }

    private ExamAttempt requireOwnedAttempt(Long examId, Long attemptId, Long userId) {
        if (attemptId == null) throw new BusinessException(400, "attemptId不能为空");
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null || !Objects.equals(attempt.getExamId(), examId) || !Objects.equals(attempt.getUserId(), userId)) {
            throw new BusinessException(404, "考试记录不存在");
        }
        return attempt;
    }

    private void saveAnswers(Exam exam, ExamAttempt attempt, List<SaveAnswerRequest.AnswerItem> items) {
        Map<Long, PaperQuestion> questions = paperQuestionMapper.selectList(new QueryWrapper<PaperQuestion>()
                        .eq("paper_id", exam.getPaperId()).orderByAsc("sort"))
                .stream().collect(Collectors.toMap(PaperQuestion::getId, q -> q));
        LocalDateTime now = LocalDateTime.now();
        for (SaveAnswerRequest.AnswerItem item : items) {
            PaperQuestion pq = questions.get(item.paperQuestionId());
            if (pq == null) throw new BusinessException(400, "题目不属于当前考试试卷: " + item.paperQuestionId());
            Answer answer = answerMapper.selectOne(new QueryWrapper<Answer>()
                    .eq("attempt_id", attempt.getId()).eq("paper_question_id", pq.getId()).last("LIMIT 1"));
            boolean isNew = answer == null;
            if (isNew) {
                answer = new Answer();
                answer.setAttemptId(attempt.getId());
                answer.setPaperQuestionId(pq.getId());
                answer.setAutoScore(BigDecimal.ZERO);
                answer.setManualScore(BigDecimal.ZERO);
                answer.setFinalScore(BigDecimal.ZERO);
            }
            answer.setAnswerContent(toJson(item.answerContent()));
            answer.setAnsweredAt(item.answerContent() == null ? null : now);
            // Use explicit insert/update so each subjective text/code answer is persisted deterministically.
            if (isNew) answerMapper.insert(answer);
            else answerMapper.updateById(answer);
        }
    }

    private void submitInternal(Exam exam, ExamAttempt attempt, List<SaveAnswerRequest.AnswerItem> ignored, boolean timeout) {
        if (!"IN_PROGRESS".equals(attempt.getStatus())) return;
        List<PaperQuestion> questions = paperQuestionMapper.selectList(new QueryWrapper<PaperQuestion>()
                .eq("paper_id", exam.getPaperId()).orderByAsc("sort"));
        ensureBlankAnswers(attempt, questions);

        BigDecimal objective = BigDecimal.ZERO;
        boolean manualRequired = false;
        for (PaperQuestion pq : questions) {
            Answer answer = getAnswer(attempt.getId(), pq.getId());
            if (OBJECTIVE_TYPES.contains(pq.getTypeSnapshot())) {
                ScoreResult score = autoScore(pq, answer);
                answer.setIsCorrect(score.correct() ? 1 : 0);
                answer.setAutoScore(score.score());
                answer.setFinalScore(score.score());
                answerMapper.updateById(answer);
                objective = objective.add(score.score());
                updateWrongQuestion(attempt.getUserId(), pq.getQuestionId(), score.correct());
            } else if (SUBJECTIVE_TYPES.contains(pq.getTypeSnapshot())) {
                manualRequired = true;
                answer.setIsCorrect(null);
                answer.setAutoScore(BigDecimal.ZERO);
                answer.setFinalScore(answer.getManualScore() == null ? BigDecimal.ZERO : answer.getManualScore());
                answerMapper.updateById(answer);
            } else {
                throw new BusinessException(409, "试卷包含暂不支持的题型：第" + pq.getQuestionNo() + "题，无法安全判分");
            }
        }

        attempt.setSubmitTime(LocalDateTime.now());
        attempt.setObjectiveScore(objective);
        attempt.setSubjectiveScore(BigDecimal.ZERO);
        attempt.setTotalScore(objective);
        attempt.setLastSavedAt(LocalDateTime.now());
        // Purely objective exams are auto-graded and published immediately after submission.
        // Exams containing short-answer or programming questions remain pending manual grading.
        attempt.setStatus(manualRequired ? "SUBMITTED" : "PUBLISHED");
        int updated = attemptMapper.update(attempt, new UpdateWrapper<ExamAttempt>()
                .eq("id", attempt.getId()).eq("status", "IN_PROGRESS"));
        if (updated != 1) throw new BusinessException(409, "试卷提交状态发生变化，请不要重复提交");

        if (timeout) {
            recordAutoSubmitEvent(exam, attempt);
        }
    }

    private void updateWrongQuestion(Long userId, Long questionId, boolean correct) {
        WrongQuestion existing = wrongQuestionMapper.selectOne(new QueryWrapper<WrongQuestion>()
                .eq("user_id", userId).eq("question_id", questionId).last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (!correct) {
            if (existing == null) {
                existing = new WrongQuestion();
                existing.setUserId(userId);
                existing.setQuestionId(questionId);
                existing.setWrongCount(1);
                existing.setRedoCount(0);
                existing.setMasteryLevel(0);
                existing.setLastWrongAt(now);
                wrongQuestionMapper.insert(existing);
            } else {
                existing.setWrongCount((existing.getWrongCount() == null ? 0 : existing.getWrongCount()) + 1);
                existing.setMasteryLevel(0);
                existing.setLastWrongAt(now);
                wrongQuestionMapper.updateById(existing);
            }
        }
    }

    private void ensureBlankAnswers(ExamAttempt attempt, List<PaperQuestion> questions) {
        LocalDateTime now = LocalDateTime.now();
        for (PaperQuestion pq : questions) {
            Answer answer = getAnswer(attempt.getId(), pq.getId());
            if (answer != null) continue;
            answer = new Answer();
            answer.setAttemptId(attempt.getId());
            answer.setPaperQuestionId(pq.getId());
            answer.setAnswerContent(null);
            answer.setIsCorrect(OBJECTIVE_TYPES.contains(pq.getTypeSnapshot()) ? 0 : null);
            answer.setAutoScore(BigDecimal.ZERO);
            answer.setManualScore(BigDecimal.ZERO);
            answer.setFinalScore(BigDecimal.ZERO);
            answer.setAnsweredAt(null);
            answerMapper.insert(answer);
        }
    }

    private ScoreResult autoScore(PaperQuestion pq, Answer answer) {
        if (answer == null || answer.getAnswerContent() == null || "null".equals(answer.getAnswerContent())) {
            return new ScoreResult(false, BigDecimal.ZERO);
        }
        String type = pq.getTypeSnapshot();
        if (!OBJECTIVE_TYPES.contains(type)) {
            throw new BusinessException(409, "题目类型不支持自动判分：" + type);
        }
        boolean correct = switch (type) {
            case "SINGLE_CHOICE", "TRUE_FALSE", "MULTIPLE_CHOICE" -> scoreChoice(pq, answer.getAnswerContent());
            case "FILL_BLANK" -> scoreFillBlank(pq, answer.getAnswerContent());
            default -> throw new BusinessException(409, "题目类型不支持自动判分：" + type);
        };
        return new ScoreResult(correct, correct ? findScore(pq) : BigDecimal.ZERO);
    }

    private boolean scoreChoice(PaperQuestion pq, String actualJson) {
        Set<String> expected = paperQuestionOptionMapper.selectList(new QueryWrapper<PaperQuestionOption>()
                        .eq("paper_question_id", pq.getId()).eq("is_correct", 1))
                .stream().map(PaperQuestionOption::getOptionKey).collect(Collectors.toCollection(TreeSet::new));
        JsonNode actual = parseJson(actualJson);
        Set<String> selected = new TreeSet<>();
        if (actual != null) {
            if (actual.isArray()) actual.forEach(n -> selected.add(n.asText()));
            else selected.add(actual.asText());
        }
        return !selected.isEmpty() && selected.equals(expected);
    }

    private boolean scoreFillBlank(PaperQuestion pq, String actualJson) {
        JsonNode config = parseJson(pq.getAnswerConfigSnapshot());
        JsonNode actual = parseJson(actualJson);
        if (config == null || actual == null || !config.has("answers")) return false;
        JsonNode expected = config.get("answers");
        if (expected == null || !expected.isArray()) return false;
        List<String> expectedValues = new ArrayList<>();
        expected.forEach(n -> expectedValues.add(normalize(n.asText())));
        List<String> actualValues = new ArrayList<>();
        if (actual.isArray()) actual.forEach(n -> actualValues.add(normalize(n.asText())));
        else actualValues.add(normalize(actual.asText()));
        return expectedValues.size() == actualValues.size() && expectedValues.equals(actualValues);
    }

    private BigDecimal findScore(PaperQuestion pq) { return pq.getScore() == null ? BigDecimal.ZERO : pq.getScore(); }

    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readTree(json); }
        catch (Exception ex) { return null; }
    }

    private Answer getAnswer(Long attemptId, Long paperQuestionId) {
        return answerMapper.selectOne(new QueryWrapper<Answer>()
                .eq("attempt_id", attemptId).eq("paper_question_id", paperQuestionId).last("LIMIT 1"));
    }

    private ExamResponse.AttemptResponse buildAttemptResponse(Exam exam, ExamAttempt attempt) {
        return new ExamResponse.AttemptResponse(exam, attempt, effectiveEnd(exam, attempt), publicQuestions(exam, attempt));
    }

    private ExamAttemptDetail buildAttemptDetail(Exam exam, ExamAttempt attempt) {
        boolean canSubmit = "IN_PROGRESS".equals(attempt.getStatus());
        return new ExamAttemptDetail(exam, attempt, effectiveEnd(exam, attempt), publicQuestions(exam, attempt), canSubmit);
    }

    private List<ExamResponse.PublicQuestion> publicQuestions(Exam exam, ExamAttempt attempt) {
        List<PaperQuestion> questions = paperQuestionMapper.selectList(new QueryWrapper<PaperQuestion>()
                .eq("paper_id", exam.getPaperId()).orderByAsc("sort").orderByAsc("question_no"));
        if (questions.isEmpty()) return List.of();
        Map<Long, Answer> answers = answerMapper.selectList(new QueryWrapper<Answer>().eq("attempt_id", attempt.getId()))
                .stream().collect(Collectors.toMap(Answer::getPaperQuestionId, a -> a));
        // One query for the options of every question instead of one query per question.
        Map<Long, List<PaperQuestionOption>> optionsByQuestion = paperQuestionOptionMapper
                .selectList(new QueryWrapper<PaperQuestionOption>()
                        .in("paper_question_id", questions.stream().map(PaperQuestion::getId).toList())
                        .orderByAsc("sort").orderByAsc("id"))
                .stream().collect(Collectors.groupingBy(PaperQuestionOption::getPaperQuestionId));
        return questions.stream().map(pq -> {
            List<PaperQuestionOption> options = optionsByQuestion.getOrDefault(pq.getId(), List.of());
            Answer saved = answers.get(pq.getId());
            return new ExamResponse.PublicQuestion(
                    pq.getId(), pq.getQuestionNo(), pq.getScore(), pq.getContentSnapshot(), pq.getTypeSnapshot(), pq.getDifficultySnapshot(),
                    options.stream().map(o -> new ExamResponse.PublicOption(o.getOptionKey(), o.getOptionContent(), o.getSort())).toList(),
                    saved == null ? null : saved.getAnswerContent());
        }).toList();
    }

    private LocalDateTime effectiveEnd(Exam exam, ExamAttempt attempt) {
        LocalDateTime byDuration = attempt.getStartTime().plusMinutes(exam.getDuration());
        return byDuration.isBefore(exam.getEndTime()) ? byDuration : exam.getEndTime();
    }

    private SubmitResponse submitResponse(ExamAttempt attempt) {
        boolean manual = "SUBMITTED".equals(attempt.getStatus());
        return new SubmitResponse(attempt.getId(), attempt.getStatus(), attempt.getObjectiveScore(),
                attempt.getSubjectiveScore(), attempt.getTotalScore(), manual);
    }

    private void recordAutoSubmitEvent(Exam exam, ExamAttempt attempt) {
        ExamEvent event = new ExamEvent();
        event.setAttemptId(attempt.getId());
        event.setUserId(attempt.getUserId());
        event.setEventType("AUTO_SUBMIT");
        event.setEventTime(LocalDateTime.now());
        event.setClientInfo("server");
        event.setMetadata(toJson(Map.of("reason", "TIMEOUT")));
        eventMapper.insert(event);
    }

    private SysUser findUser(String username) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
        if (user == null) throw new BusinessException(401, "当前用户不存在");
        return user;
    }

    private boolean hasRole(Long userId, String roleCode) {
        return userMapper.findRoleCodes(userId).contains(roleCode);
    }

    private String toJson(Object object) {
        if (object == null) return null;
        try { return objectMapper.writeValueAsString(object); }
        catch (Exception ex) { throw new BusinessException(400, "答案JSON格式无效"); }
    }

    private String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }

    private record ScoreResult(boolean correct, BigDecimal score) {}
}
