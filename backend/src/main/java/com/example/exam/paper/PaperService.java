package com.example.exam.paper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.question.Question;
import com.example.exam.question.QuestionMapper;
import com.example.exam.question.QuestionOption;
import com.example.exam.question.QuestionOptionMapper;
import com.example.exam.subject.SubjectMapper;
import com.example.exam.knowledge.KnowledgePointMapper;
import com.example.exam.exam.Exam;
import com.example.exam.exam.ExamMapper;
import com.example.exam.exam.ExamAttempt;
import com.example.exam.exam.ExamAttemptMapper;
import com.example.exam.knowledge.KnowledgePoint;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PaperService {
    private static final Set<String> TYPES = Set.of(
            "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER", "PROGRAMMING");
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    private final PaperMapper paperMapper;
    private final ExamMapper examMapper;
    private final ExamAttemptMapper attemptMapper;
    private final PaperQuestionMapper paperQuestionMapper;
    private final PaperQuestionOptionMapper paperQuestionOptionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final SubjectMapper subjectMapper;
    private final SysUserMapper userMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final ObjectMapper objectMapper;

    public PaperService(PaperMapper paperMapper,
                        ExamMapper examMapper,
                        ExamAttemptMapper attemptMapper,
                        PaperQuestionMapper paperQuestionMapper,
                        PaperQuestionOptionMapper paperQuestionOptionMapper,
                        QuestionMapper questionMapper,
                        QuestionOptionMapper questionOptionMapper,
                        SubjectMapper subjectMapper,
                        SysUserMapper userMapper,
                        KnowledgePointMapper knowledgePointMapper,
                        ObjectMapper objectMapper) {
        this.paperMapper = paperMapper;
        this.examMapper = examMapper;
        this.attemptMapper = attemptMapper;
        this.paperQuestionMapper = paperQuestionMapper;
        this.paperQuestionOptionMapper = paperQuestionOptionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.subjectMapper = subjectMapper;
        this.userMapper = userMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.objectMapper = objectMapper;
    }

    public Page<Paper> page(int page, int size, Long subjectId, String type, String status) {
        Page<Paper> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        QueryWrapper<Paper> query = new QueryWrapper<>();
        query.eq(subjectId != null, "subject_id", subjectId)
                .eq(type != null && !type.isBlank(), "type", upper(type))
                .eq(status != null && !status.isBlank(), "status", upper(status))
                .orderByDesc("id");
        return paperMapper.selectPage(p, query);
    }

    public PaperResponse getDetail(Long id, boolean revealAnswer) {
        Paper paper = get(id);
        List<PaperQuestion> pqs = paperQuestionMapper.selectList(new QueryWrapper<PaperQuestion>()
                .eq("paper_id", id).orderByAsc("sort").orderByAsc("question_no"));
        List<QuestionSnapshotDto> snapshots = pqs.stream().map(pq -> {
            List<PaperQuestionOption> options = paperQuestionOptionMapper.selectList(
                    new QueryWrapper<PaperQuestionOption>().eq("paper_question_id", pq.getId())
                            .orderByAsc("sort").orderByAsc("id"));
            return new QuestionSnapshotDto(pq, options);
        }).toList();
        return new PaperResponse(paper, snapshots.stream().map(s -> new PaperResponse.QuestionSnapshot(
                s.question.getId(), s.question.getQuestionId(), s.question.getQuestionNo(), s.question.getScore(),
                s.question.getContentSnapshot(), s.question.getTypeSnapshot(), s.question.getAnalysisSnapshot(),
                s.question.getDifficultySnapshot(), revealAnswer ? s.question.getAnswerConfigSnapshot() : null,
                s.options.stream().map(o -> new PaperResponse.OptionSnapshot(o.getOptionKey(), o.getOptionContent(), o.getSort(),
                        revealAnswer && Integer.valueOf(1).equals(o.getIsCorrect()))).toList()
        )).toList());
    }

    public Paper get(Long id) {
        Paper paper = paperMapper.selectById(id);
        if (paper == null) throw new BusinessException(404, "试卷不存在");
        return paper;
    }

    @Transactional
    public PaperResponse create(PaperRequest request, String username) {
        String type = upper(request.type());
        validateBase(request, type);
        SysUser creator = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
        if (creator == null) throw new BusinessException(401, "当前用户不存在");

        Paper paper = new Paper();
        paper.setName(request.name().trim());
        paper.setSubjectId(request.subjectId());
        paper.setType(type);
        paper.setDuration(request.duration());
        paper.setCreatorId(creator.getId());
        paper.setStatus("DRAFT");

        if ("FIXED".equals(type)) {
            List<PaperQuestion> snapshots = buildFixedSnapshots(request.questions(), request.subjectId());
            paper.setTotalScore(snapshots.stream().map(PaperQuestion::getScore).reduce(BigDecimal.ZERO, BigDecimal::add));
            paperMapper.insert(paper);
            persistSnapshots(paper.getId(), snapshots);
        } else {
            paper.setTotalScore(request.ruleConfig().totalScore());
            try {
                paper.setRuleConfig(objectMapper.writeValueAsString(request.ruleConfig()));
            } catch (Exception ex) {
                throw new BusinessException(400, "组卷规则格式无效");
            }
            paperMapper.insert(paper);
            List<PaperQuestion> snapshots = buildRuleSnapshots(request.ruleConfig(), request.subjectId());
            persistSnapshots(paper.getId(), snapshots);
        }
        return getDetail(paper.getId(), true);
    }

    @Transactional
    public PaperResponse update(Long id, PaperRequest request, String username, boolean admin) {
        Paper paper = get(id);
        SysUser operator = findUser(username);
        if (!admin && !Objects.equals(paper.getCreatorId(), operator.getId())) {
            throw new BusinessException(403, "只能修改自己创建的试卷");
        }
        if (!"DRAFT".equals(paper.getStatus()) && !"PUBLISHED".equals(paper.getStatus())) {
            throw new BusinessException(409, "只有草稿或已发布试卷可以修改，已归档试卷不能修改");
        }
        if ("PUBLISHED".equals(paper.getStatus())) {
            assertPublishedPaperEditable(id);
        }
        String type = upper(request.type());
        validateBase(request, type);
        Paper replacement = new Paper();
        replacement.setId(id);
        replacement.setName(request.name().trim());
        replacement.setSubjectId(request.subjectId());
        replacement.setDuration(request.duration());
        replacement.setType(type);
        replacement.setStatus(paper.getStatus()); // 编辑已发布试卷时保留发布状态

        List<PaperQuestion> oldQuestions = paperQuestionMapper.selectList(
                new QueryWrapper<PaperQuestion>().eq("paper_id", id));
        for (PaperQuestion oldQuestion : oldQuestions) {
            paperQuestionOptionMapper.delete(new QueryWrapper<PaperQuestionOption>()
                    .eq("paper_question_id", oldQuestion.getId()));
        }
        paperQuestionMapper.delete(new QueryWrapper<PaperQuestion>().eq("paper_id", id));

        if ("FIXED".equals(type)) {
            List<PaperQuestion> snapshots = buildFixedSnapshots(request.questions(), request.subjectId());
            replacement.setTotalScore(snapshots.stream().map(PaperQuestion::getScore).reduce(BigDecimal.ZERO, BigDecimal::add));
            replacement.setRuleConfig(null);
            paperMapper.updateById(replacement);
            persistSnapshots(id, snapshots);
        } else {
            replacement.setTotalScore(request.ruleConfig().totalScore());
            try {
                replacement.setRuleConfig(objectMapper.writeValueAsString(request.ruleConfig()));
            } catch (Exception ex) {
                throw new BusinessException(400, "组卷规则格式无效");
            }
            paperMapper.updateById(replacement);
            persistSnapshots(id, buildRuleSnapshots(request.ruleConfig(), request.subjectId()));
        }
        return getDetail(id, true);
    }

    /**
     * 已发布试卷允许编辑，但只要关联考试已经开始、结束或产生过答卷，就必须锁定试卷内容，
     * 以免改变进行中的答题内容或影响历史成绩。关联的未来考试如果尚未产生答卷，可以使用更新后的试卷。
     */
    private void assertPublishedPaperEditable(Long paperId) {
        List<Exam> linkedExams = examMapper.selectList(new QueryWrapper<Exam>().eq("paper_id", paperId));
        LocalDateTime now = LocalDateTime.now();
        for (Exam exam : linkedExams) {
            if ("ARCHIVED".equalsIgnoreCase(exam.getStatus())
                    || (exam.getStartTime() != null && !now.isBefore(exam.getStartTime()))
                    || (exam.getEndTime() != null && !now.isBefore(exam.getEndTime()))) {
                throw new BusinessException(409, "该试卷关联的考试“" + exam.getName()
                        + "”已经开始或结束，不能修改试卷内容，以保护考试历史记录");
            }
            long attempts = attemptMapper.selectCount(new QueryWrapper<ExamAttempt>().eq("exam_id", exam.getId()));
            if (attempts > 0) {
                throw new BusinessException(409, "该试卷关联的考试“" + exam.getName()
                        + "”已有答卷，不能修改试卷内容，以保护答题和成绩数据");
            }
        }
    }

    @Transactional
    public int deleteBatch(List<Long> ids, String username, boolean admin) {
        if (ids == null || ids.isEmpty()) throw new BusinessException(400, "请至少选择一份试卷");
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) throw new BusinessException(400, "请选择有效的试卷 ID");
        if (distinctIds.size() > 100) throw new BusinessException(400, "单次最多删除 100 份试卷");

        SysUser operator = findUser(username);
        List<Paper> papers = paperMapper.selectBatchIds(distinctIds);
        if (papers.size() != distinctIds.size()) throw new BusinessException(404, "部分试卷不存在或已被删除，请刷新列表");
        for (Paper paper : papers) {
            if (!admin && !Objects.equals(paper.getCreatorId(), operator.getId())) {
                throw new BusinessException(403, "只能删除自己创建的试卷");
            }
        }
        List<Exam> linkedExams = examMapper.selectList(new QueryWrapper<Exam>().in("paper_id", distinctIds));
        if (!linkedExams.isEmpty()) {
            Set<Long> linkedPaperIds = linkedExams.stream().map(Exam::getPaperId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            throw new BusinessException(409, "试卷 " + linkedPaperIds + " 已被考试引用，不能删除；为保护考试历史，请先保留这些试卷");
        }
        for (Long id : distinctIds) {
            paperMapper.deleteById(id); // 试卷题目和选项由数据库外键级联清理
        }
        return distinctIds.size();
    }

    private SysUser findUser(String username) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
        if (user == null) throw new BusinessException(401, "当前用户不存在");
        return user;
    }

    @Transactional
    public void publish(Long id) {
        Paper paper = get(id);
        if (!"DRAFT".equals(paper.getStatus())) throw new BusinessException(409, "只有草稿试卷才能发布");
        long questionCount = paperQuestionMapper.selectCount(new QueryWrapper<PaperQuestion>().eq("paper_id", id));
        if (questionCount == 0) throw new BusinessException(400, "试卷至少需要包含一道题目");
        paper.setStatus("PUBLISHED");
        paperMapper.updateById(paper);
    }

    private void validateBase(PaperRequest request, String type) {
        if (!"FIXED".equals(type) && !"RULE".equals(type)) throw new BusinessException(400, "试卷类型只能是FIXED或RULE");
        if (subjectMapper.selectById(request.subjectId()) == null) throw new BusinessException(404, "学科不存在");
        if ("FIXED".equals(type)) {
            if (request.questions() == null || request.questions().isEmpty()) throw new BusinessException(400, "固定试卷至少需要一道题目");
            if (request.ruleConfig() != null) throw new BusinessException(400, "固定试卷不应提供动态组卷规则");
        } else if (request.ruleConfig() == null) {
            throw new BusinessException(400, "规则试卷必须提供组卷规则");
        }
    }

    private List<PaperQuestion> buildFixedSnapshots(List<PaperRequest.FixedQuestionItem> items, Long subjectId) {
        Set<Long> ids = new HashSet<>();
        List<PaperQuestion> result = new ArrayList<>();
        int no = 1;
        for (PaperRequest.FixedQuestionItem item : items) {
            if (!ids.add(item.questionId())) throw new BusinessException(400, "固定试卷存在重复题目: " + item.questionId());
            Question q = enabledQuestion(item.questionId(), subjectId);
            result.add(snapshot(q, no++, item.score()));
        }
        return result;
    }

    private List<PaperQuestion> buildRuleSnapshots(PaperRequest.RuleConfig rule, Long subjectId) {
        validateRule(rule);
        List<Question> selected = new ArrayList<>();
        Random random = new Random();

        for (PaperRequest.TypeRule typeRule : rule.types()) {
            String type = upper(typeRule.type());
            int target = typeRule.count();
            Map<String, Integer> counts = allocateDifficulty(target, rule.difficulty());

            if (counts.isEmpty()) {
                selected.addAll(select(subjectId, type, null, rule.knowledgePointIds(), target, Set.copyOf(idsOf(selected)), random));
            } else {
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    int count = entry.getValue();
                    if (count <= 0) continue;
                    selected.addAll(select(subjectId, type, entry.getKey(), rule.knowledgePointIds(), count,
                            Set.copyOf(idsOf(selected)), random));
                }
            }
        }

        if (selected.size() != rule.types().stream().mapToInt(PaperRequest.TypeRule::count).sum()) {
            throw new BusinessException(409, "动态组卷数量校验失败");
        }
        Set<Long> ids = idsOf(selected);
        if (ids.size() != selected.size()) throw new BusinessException(409, "动态组卷产生重复题目，请重试");

        BigDecimal actualScore = BigDecimal.ZERO;
        Map<String, BigDecimal> scoreMap = rule.types().stream()
                .collect(Collectors.toMap(r -> upper(r.type()), PaperRequest.TypeRule::score));
        for (Question q : selected) actualScore = actualScore.add(scoreMap.get(q.getType()));
        if (actualScore.compareTo(rule.totalScore()) != 0) {
            throw new BusinessException(409, "组卷规则的题型分值合计为 " + actualScore + "，与总分 " + rule.totalScore() + " 不一致");
        }

        List<PaperQuestion> snapshots = new ArrayList<>(selected.size());
        int questionNo = 1;
        for (Question question : selected) {
            snapshots.add(snapshot(question, questionNo++, scoreMap.get(question.getType())));
        }
        return snapshots;
    }

    private List<Question> select(Long subjectId, String type, String difficulty, List<Long> knowledgePointIds,
                                  int count, Set<Long> alreadySelected, Random random) {
        QueryWrapper<Question> query = new QueryWrapper<Question>()
                .eq("subject_id", subjectId)
                .eq("type", type)
                .eq("status", 1);
        if (difficulty != null) query.eq("difficulty", difficulty);
        if (knowledgePointIds != null && !knowledgePointIds.isEmpty()) query.in("knowledge_point_id", knowledgePointIds);
        List<Question> candidates = questionMapper.selectList(query);
        List<Question> available = candidates.stream().filter(q -> !alreadySelected.contains(q.getId())).collect(Collectors.toCollection(ArrayList::new));
        if (available.size() < count) {
            String scope = difficulty == null ? "不限难度" : difficulty;
            throw new BusinessException(409, "题库数量不足：题型=" + type + "，难度=" + scope + "，需要=" + count + "，可用=" + available.size());
        }
        Collections.shuffle(available, random);
        return new ArrayList<>(available.subList(0, count));
    }

    private Map<String, Integer> allocateDifficulty(int total, Map<String, BigDecimal> difficultyRatio) {
        if (difficultyRatio == null || difficultyRatio.isEmpty()) return Map.of();
        Map<String, BigDecimal> ratios = new LinkedHashMap<>();
        difficultyRatio.forEach((key, value) -> ratios.put(upper(key), value));
        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, BigDecimal> remainder = new HashMap<>();
        int assigned = 0;
        for (String difficulty : List.of("EASY", "MEDIUM", "HARD")) {
            BigDecimal ratio = ratios.getOrDefault(difficulty, BigDecimal.ZERO);
            BigDecimal raw = BigDecimal.valueOf(total).multiply(ratio);
            int base = raw.setScale(0, RoundingMode.FLOOR).intValue();
            result.put(difficulty, base);
            assigned += base;
            remainder.put(difficulty, raw.subtract(BigDecimal.valueOf(base)));
        }
        int left = total - assigned;
        List<String> order = remainder.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
        for (int i = 0; i < left; i++) result.merge(order.get(i % order.size()), 1, Integer::sum);
        return result;
    }

    private void validateRule(PaperRequest.RuleConfig rule) {
        Set<String> types = new HashSet<>();
        int totalCount = 0;
        for (PaperRequest.TypeRule type : rule.types()) {
            String code = upper(type.type());
            if (!TYPES.contains(code)) throw new BusinessException(400, "不支持的组卷题型: " + type.type());
            if (!types.add(code)) throw new BusinessException(400, "组卷规则存在重复题型: " + code);
            totalCount += type.count();
        }
        if (totalCount <= 0) throw new BusinessException(400, "组卷题目数量必须大于0");
        if (rule.difficulty() != null && !rule.difficulty().isEmpty()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> entry : rule.difficulty().entrySet()) {
                String difficulty = upper(entry.getKey());
                if (!DIFFICULTIES.contains(difficulty)) throw new BusinessException(400, "不支持的难度: " + entry.getKey());
                if (entry.getValue() == null || entry.getValue().compareTo(BigDecimal.ZERO) < 0) throw new BusinessException(400, "难度比例不能为负数");
                sum = sum.add(entry.getValue());
            }
            if (sum.compareTo(BigDecimal.ONE) != 0) throw new BusinessException(400, "难度比例之和必须等于1");
        }
        if (rule.knowledgePointIds() != null && !rule.knowledgePointIds().isEmpty()) {
            long existing = knowledgePointMapper.selectCount(new QueryWrapper<KnowledgePoint>()
                    .in("id", rule.knowledgePointIds()));
            if (existing != rule.knowledgePointIds().stream().distinct().count()) {
                throw new BusinessException(400, "组卷规则中存在不存在的知识点");
            }
            if (knowledgePointMapper.selectCount(new QueryWrapper<KnowledgePoint>()
                    .in("id", rule.knowledgePointIds()).ne("status", 1)) > 0) {
                throw new BusinessException(400, "组卷规则中包含停用知识点");
            }
        }
    }

    private Question enabledQuestion(Long id, Long subjectId) {
        Question q = questionMapper.selectById(id);
        if (q == null) throw new BusinessException(404, "题目不存在: " + id);
        if (!Integer.valueOf(1).equals(q.getStatus())) throw new BusinessException(409, "题目已停用: " + id);
        if (!subjectId.equals(q.getSubjectId())) throw new BusinessException(400, "题目不属于当前试卷学科: " + id);
        return q;
    }

    private PaperQuestion snapshot(Question q, int no, BigDecimal score) {
        PaperQuestion pq = new PaperQuestion();
        pq.setQuestionId(q.getId());
        pq.setQuestionNo(no);
        pq.setScore(score);
        pq.setSort(no);
        pq.setContentSnapshot(q.getContent());
        pq.setTypeSnapshot(q.getType());
        pq.setAnswerConfigSnapshot(q.getAnswerConfig());
        pq.setAnalysisSnapshot(q.getAnalysis());
        pq.setDifficultySnapshot(q.getDifficulty());
        return pq;
    }

    private void persistSnapshots(Long paperId, List<PaperQuestion> snapshots) {
        Set<Long> questionIds = snapshots.stream().map(PaperQuestion::getQuestionId).collect(Collectors.toSet());
        Map<Long, List<QuestionOption>> options = new HashMap<>();
        if (!questionIds.isEmpty()) {
            questionOptionMapper.selectList(new QueryWrapper<QuestionOption>().in("question_id", questionIds))
                    .forEach(o -> options.computeIfAbsent(o.getQuestionId(), k -> new ArrayList<>()).add(o));
        }
        for (PaperQuestion snapshot : snapshots) {
            snapshot.setPaperId(paperId);
            paperQuestionMapper.insert(snapshot);
            List<QuestionOption> sourceOptions = options.getOrDefault(snapshot.getQuestionId(), List.of());
            for (QuestionOption source : sourceOptions) {
                PaperQuestionOption target = new PaperQuestionOption();
                target.setPaperQuestionId(snapshot.getId());
                target.setOptionKey(source.getOptionKey());
                target.setOptionContent(source.getOptionContent());
                target.setSort(source.getSort());
                target.setIsCorrect(source.getIsCorrect());
                paperQuestionOptionMapper.insert(target);
            }
        }
    }

    private Set<Long> idsOf(Collection<Question> questions) {
        return questions.stream().map(Question::getId).collect(Collectors.toSet());
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private record QuestionSnapshotDto(PaperQuestion question, List<PaperQuestionOption> options) {}
}
