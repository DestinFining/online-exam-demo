package com.example.exam.grade;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.exam.Answer;
import com.example.exam.exam.AnswerMapper;
import com.example.exam.exam.Exam;
import com.example.exam.exam.ExamAttempt;
import com.example.exam.exam.ExamAttemptMapper;
import com.example.exam.paper.PaperQuestion;
import com.example.exam.paper.PaperQuestionMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.wrongquestion.WrongQuestion;
import com.example.exam.wrongquestion.WrongQuestionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GradeService {
    private static final Set<String> SUBJECTIVE_TYPES = Set.of("SHORT_ANSWER", "PROGRAMMING");

    private final ExamAttemptMapper attemptMapper;
    private final AnswerMapper answerMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final SysUserMapper userMapper;
    private final com.example.exam.exam.ExamMapper examMapper;
    private final WrongQuestionMapper wrongQuestionMapper;

    public GradeService(ExamAttemptMapper attemptMapper,
                        AnswerMapper answerMapper,
                        PaperQuestionMapper paperQuestionMapper,
                        SysUserMapper userMapper,
                        com.example.exam.exam.ExamMapper examMapper,
                        WrongQuestionMapper wrongQuestionMapper) {
        this.attemptMapper = attemptMapper;
        this.answerMapper = answerMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.userMapper = userMapper;
        this.examMapper = examMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
    }

    public List<GradeSummary> examSubmissions(Long examId, String username, boolean admin) {
        Exam exam = requireExam(examId);
        SysUser operator = requireUser(username);
        requireTeacherAccess(exam, operator, admin);
        List<ExamAttempt> attempts = attemptMapper.selectList(new QueryWrapper<ExamAttempt>()
                .eq("exam_id", examId)
                .in("status", List.of("SUBMITTED", "GRADED", "PUBLISHED"))
                .orderByDesc("submit_time"));
        return attempts.stream().map(this::toSummary).toList();
    }

    @Transactional
    public GradeAttemptDetail detail(Long attemptId, String username, boolean admin) {
        ExamAttempt attempt = requireAttempt(attemptId);
        Exam exam = requireExam(attempt.getExamId());
        SysUser operator = requireUser(username);
        boolean owner = Objects.equals(operator.getId(), attempt.getUserId());
        boolean teacher = hasRole(operator.getId(), "TEACHER") || hasRole(operator.getId(), "ADMIN");
        if (!owner && !teacher) throw new BusinessException(403, "无权查看该答卷");
        if (teacher) requireTeacherAccess(exam, operator, admin || hasRole(operator.getId(), "ADMIN"));
        if (owner && !"PUBLISHED".equals(attempt.getStatus())) {
            throw new BusinessException(409, "成绩尚未发布");
        }
        // Repair legacy/incomplete submissions before returning the teacher's grading sheet.
        // Every paper question must have an answer row, including unanswered subjective items.
        if (teacher) ensureAnswerRows(attempt, exam);
        List<GradeAnswerView> rows = buildAnswerViews(attempt);
        boolean manualComplete = rows.stream().noneMatch(r -> SUBJECTIVE_TYPES.contains(r.type()) && r.gradedAt() == null);
        if (owner && !teacher) {
            // The status guard above already keeps an unpublished result away from its
            // owner; the redaction keeps the reference answer and grading rubric away
            // from the student even once the score is published, matching the UI.
            rows = rows.stream().map(this::redactForStudent).toList();
        }
        return new GradeAttemptDetail(toSummary(attempt), rows, manualComplete);
    }

    @Transactional
    public GradeAttemptDetail gradeAnswer(Long answerId, GradeAnswerRequest request, String username, boolean admin) {
        Answer answer = requireAnswer(answerId);
        ExamAttempt attempt = requireAttempt(answer.getAttemptId());
        Exam exam = requireExam(attempt.getExamId());
        SysUser teacher = requireUser(username);
        requireTeacherAccess(exam, teacher, admin || hasRole(teacher.getId(), "ADMIN"));
        PaperQuestion question = requirePaperQuestion(answer.getPaperQuestionId());
        if (!SUBJECTIVE_TYPES.contains(question.getTypeSnapshot())) {
            throw new BusinessException(409, "客观题不能人工修改分数");
        }
        BigDecimal max = question.getScore() == null ? BigDecimal.ZERO : question.getScore();
        if (request.score().compareTo(max) > 0) throw new BusinessException(400, "评分不能超过题目满分 " + max);

        answer.setManualScore(request.score());
        answer.setFinalScore(request.score());
        answer.setTeacherComment(request.comment());
        answer.setGradedAt(LocalDateTime.now());
        answerMapper.updateById(answer);
        return detailForTeacher(attempt, teacher, admin);
    }

    @Transactional
    public GradeSummary finalizeAttempt(Long attemptId, String username, boolean admin) {
        ExamAttempt attempt = requireAttempt(attemptId);
        Exam exam = requireExam(attempt.getExamId());
        SysUser teacher = requireUser(username);
        requireTeacherAccess(exam, teacher, admin || hasRole(teacher.getId(), "ADMIN"));
        if (!Set.of("SUBMITTED", "GRADED").contains(attempt.getStatus())) {
            throw new BusinessException(409, "当前答卷状态不能结算：" + attempt.getStatus());
        }

        ensureAnswerRows(attempt, exam);
        List<PaperQuestion> questions = paperQuestionMapper.selectList(new QueryWrapper<PaperQuestion>()
                .eq("paper_id", exam.getPaperId()).orderByAsc("sort"));
        BigDecimal objective = BigDecimal.ZERO;
        BigDecimal subjective = BigDecimal.ZERO;
        for (PaperQuestion pq : questions) {
            Answer answer = findAnswer(attempt.getId(), pq.getId());
            if (answer == null) {
                if (SUBJECTIVE_TYPES.contains(pq.getTypeSnapshot())) {
                    throw new BusinessException(409, "第" + pq.getQuestionNo() + "题缺少答题记录，请重新打开阅卷详情后重试");
                }
                continue;
            }
            if (SUBJECTIVE_TYPES.contains(pq.getTypeSnapshot())) {
                if (answer.getManualScore() == null || answer.getGradedAt() == null) {
                    throw new BusinessException(409, "存在未批改的主观题：第" + pq.getQuestionNo() + "题");
                }
                subjective = subjective.add(answer.getManualScore());
                answer.setFinalScore(answer.getManualScore());
                answerMapper.updateById(answer);
            } else {
                objective = objective.add(nz(answer.getAutoScore()));
                answer.setFinalScore(nz(answer.getAutoScore()));
                answerMapper.updateById(answer);
            }
        }
        attempt.setObjectiveScore(objective);
        attempt.setSubjectiveScore(subjective);
        attempt.setTotalScore(objective.add(subjective));
        attempt.setStatus("GRADED");
        attemptMapper.updateById(attempt);
        return toSummary(attempt);
    }

    @Transactional
    public GradeSummary publishAttempt(Long attemptId, String username, boolean admin) {
        ExamAttempt attempt = requireAttempt(attemptId);
        Exam exam = requireExam(attempt.getExamId());
        SysUser teacher = requireUser(username);
        requireTeacherAccess(exam, teacher, admin || hasRole(teacher.getId(), "ADMIN"));
        if (!"GRADED".equals(attempt.getStatus())) throw new BusinessException(409, "只有已阅卷答卷才能发布成绩");
        attempt.setStatus("PUBLISHED");
        attemptMapper.updateById(attempt);
        return toSummary(attempt);
    }

    @Transactional
    public BatchPublishResult publishAllForExam(Long examId, String username, boolean admin) {
        Exam exam = requireExam(examId);
        SysUser teacher = requireUser(username);
        requireTeacherAccess(exam, teacher, admin || hasRole(teacher.getId(), "ADMIN"));

        List<ExamAttempt> attempts = attemptMapper.selectList(new QueryWrapper<ExamAttempt>()
                .eq("exam_id", examId)
                .in("status", List.of("SUBMITTED", "GRADED", "PUBLISHED"))
                .orderByAsc("id"));
        if (attempts.isEmpty()) {
            throw new BusinessException(409, "本场考试还没有可发布成绩的已提交答卷");
        }

        int alreadyPublished = (int) attempts.stream().filter(a -> "PUBLISHED".equals(a.getStatus())).count();
        int newlyPublished = 0;
        // This method is transactional: if any submission still has an ungraded subjective
        // question, the whole operation rolls back and no student's result is partially published.
        for (ExamAttempt listedAttempt : attempts) {
            ExamAttempt current = requireAttempt(listedAttempt.getId());
            if ("PUBLISHED".equals(current.getStatus())) continue;
            try {
                if ("SUBMITTED".equals(current.getStatus())) {
                    finalizeAttempt(current.getId(), username, admin || hasRole(teacher.getId(), "ADMIN"));
                    current = requireAttempt(current.getId());
                }
                if (!"GRADED".equals(current.getStatus())) {
                    throw new BusinessException(409, "答卷状态为 " + current.getStatus() + "，不能发布");
                }
                publishAttempt(current.getId(), username, admin || hasRole(teacher.getId(), "ADMIN"));
                newlyPublished++;
            } catch (BusinessException ex) {
                throw new BusinessException(ex.getCode(), candidateLabel(current) + "发布失败：" + ex.getMessage());
            }
        }
        return new BatchPublishResult(examId, newlyPublished, alreadyPublished);
    }

    public List<GradeSummary> myResults(String username) {
        SysUser user = requireUser(username);
        return attemptMapper.selectList(new QueryWrapper<ExamAttempt>()
                        .eq("user_id", user.getId())
                        .in("status", List.of("SUBMITTED", "GRADED", "PUBLISHED"))
                        .orderByDesc("submit_time"))
                .stream().map(attempt -> {
                    if ("PUBLISHED".equals(attempt.getStatus())) return toSummary(attempt);
                    // Show the student that a submission exists, without leaking an unpublished score.
                    return summaryWithoutScores(attempt);
                }).toList();
    }

    private void ensureAnswerRows(ExamAttempt attempt, Exam exam) {
        List<PaperQuestion> questions = paperQuestionMapper.selectList(new QueryWrapper<PaperQuestion>()
                .eq("paper_id", exam.getPaperId()).orderByAsc("sort"));
        if (questions.isEmpty()) return;
        Set<Long> existingIds = new HashSet<>();
        answerMapper.selectList(new QueryWrapper<Answer>().eq("attempt_id", attempt.getId()))
                .forEach(a -> existingIds.add(a.getPaperQuestionId()));
        for (PaperQuestion question : questions) {
            if (!existingIds.add(question.getId())) continue;
            Answer missing = new Answer();
            missing.setAttemptId(attempt.getId());
            missing.setPaperQuestionId(question.getId());
            missing.setAnswerContent(null);
            missing.setIsCorrect(SUBJECTIVE_TYPES.contains(question.getTypeSnapshot()) ? null : 0);
            missing.setAutoScore(BigDecimal.ZERO);
            missing.setManualScore(BigDecimal.ZERO);
            missing.setFinalScore(BigDecimal.ZERO);
            answerMapper.insert(missing);
        }
    }

    private GradeAttemptDetail detailForTeacher(ExamAttempt attempt, SysUser teacher, boolean admin) {
        List<GradeAnswerView> rows = buildAnswerViews(attempt);
        boolean manualComplete = rows.stream().noneMatch(r -> SUBJECTIVE_TYPES.contains(r.type()) && r.gradedAt() == null);
        return new GradeAttemptDetail(toSummary(attempt), rows, manualComplete);
    }

    private List<GradeAnswerView> buildAnswerViews(ExamAttempt attempt) {
        List<Answer> answers = answerMapper.selectList(new QueryWrapper<Answer>()
                .eq("attempt_id", attempt.getId()).orderByAsc("paper_question_id"));
        if (answers.isEmpty()) return List.of();
        // Load every referenced paper question in one query instead of one query per answer.
        Map<Long, PaperQuestion> questionsById = paperQuestionMapper
                .selectBatchIds(answers.stream().map(Answer::getPaperQuestionId).distinct().toList())
                .stream().collect(Collectors.toMap(PaperQuestion::getId, question -> question));
        return answers.stream().map(answer -> {
            PaperQuestion pq = questionsById.get(answer.getPaperQuestionId());
            if (pq == null) throw new BusinessException(404, "试卷题目不存在：" + answer.getPaperQuestionId());
            return new GradeAnswerView(
                    answer.getId(), pq.getId(), pq.getQuestionId(), pq.getQuestionNo(), pq.getTypeSnapshot(),
                    pq.getContentSnapshot(), answer.getAnswerContent(), pq.getAnswerConfigSnapshot(), pq.getScore(),
                    answer.getAutoScore(), answer.getManualScore(), answer.getFinalScore(), answer.getIsCorrect(), answer.getTeacherComment(), answer.getGradedAt());
        }).toList();
    }

    /**
     * Rebuilds the row for the attempt's own student. {@code referenceAnswer} carries the
     * answer-configuration snapshot, which for short-answer and programming questions is the
     * grading rubric, so it is cleared instead of copied.
     */
    private GradeAnswerView redactForStudent(GradeAnswerView row) {
        return new GradeAnswerView(row.answerId(), row.paperQuestionId(), row.questionId(), row.questionNo(),
                row.type(), row.content(), row.answerContent(), null, row.maxScore(), row.autoScore(),
                row.manualScore(), row.finalScore(), row.isCorrect(), row.teacherComment(), row.gradedAt());
    }

    private GradeSummary toSummary(ExamAttempt attempt) {
        return new GradeSummary(attempt.getId(), attempt.getExamId(), candidateLabel(attempt), attempt.getAttemptNo(),
                attempt.getStatus(), nz(attempt.getObjectiveScore()), nz(attempt.getSubjectiveScore()), nz(attempt.getTotalScore()),
                attempt.getCheatCount(), attempt.getStartTime(), attempt.getSubmitTime());
    }

    private GradeSummary summaryWithoutScores(ExamAttempt attempt) {
        return new GradeSummary(attempt.getId(), attempt.getExamId(), candidateLabel(attempt), attempt.getAttemptNo(),
                attempt.getStatus(), null, null, null,
                attempt.getCheatCount(), attempt.getStartTime(), attempt.getSubmitTime());
    }

    private String candidateLabel(ExamAttempt attempt) {
        // Stable anonymized label so a teacher can revisit a submission without seeing identity fields.
        return "匿名考生-" + String.format(Locale.ROOT, "%06d", attempt.getId());
    }

    private void requireTeacherAccess(Exam exam, SysUser operator, boolean admin) {
        if (admin) return;
        if (!hasRole(operator.getId(), "TEACHER")) throw new BusinessException(403, "需要教师或管理员权限");
        if (!Objects.equals(exam.getCreatorId(), operator.getId())) throw new BusinessException(403, "只能批改自己创建考试的答卷");
    }

    private boolean hasRole(Long userId, String role) { return userMapper.findRoleCodes(userId).contains(role); }
    private SysUser requireUser(String username) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
        if (user == null) throw new BusinessException(401, "当前用户不存在");
        return user;
    }
    private Exam requireExam(Long id) { Exam exam = examMapper.selectById(id); if (exam == null) throw new BusinessException(404, "考试不存在"); return exam; }
    private ExamAttempt requireAttempt(Long id) { ExamAttempt a = attemptMapper.selectById(id); if (a == null) throw new BusinessException(404, "考试记录不存在"); return a; }
    private Answer requireAnswer(Long id) { Answer a = answerMapper.selectById(id); if (a == null) throw new BusinessException(404, "答题记录不存在"); return a; }
    private PaperQuestion requirePaperQuestion(Long id) { PaperQuestion q = paperQuestionMapper.selectById(id); if (q == null) throw new BusinessException(404, "试卷题目不存在"); return q; }
    private Answer findAnswer(Long attemptId, Long paperQuestionId) { return answerMapper.selectOne(new QueryWrapper<Answer>().eq("attempt_id", attemptId).eq("paper_question_id", paperQuestionId).last("LIMIT 1")); }
    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
