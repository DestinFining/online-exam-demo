package com.example.exam.question;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.config.RedisJsonCache;
import com.example.exam.knowledge.KnowledgePoint;
import com.example.exam.knowledge.KnowledgePointMapper;
import com.example.exam.subject.SubjectMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

@Service
public class QuestionService {
    private static final java.util.Set<String> TYPES = java.util.Set.of(
            "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE", "FILL_BLANK", "SHORT_ANSWER", "PROGRAMMING");
    private static final java.util.Set<String> DIFFICULTIES = java.util.Set.of("EASY", "MEDIUM", "HARD");

    private final QuestionMapper mapper;
    private final QuestionOptionMapper optionMapper;
    private final SysUserMapper userMapper;
    private final SubjectMapper subjectMapper;
    private final KnowledgePointMapper knowledgePointMapper;
    private final RedisJsonCache cache;

    public QuestionService(QuestionMapper mapper,
                           QuestionOptionMapper optionMapper,
                           SysUserMapper userMapper,
                           SubjectMapper subjectMapper,
                           KnowledgePointMapper knowledgePointMapper,
                           RedisJsonCache cache) {
        this.mapper = mapper;
        this.optionMapper = optionMapper;
        this.userMapper = userMapper;
        this.subjectMapper = subjectMapper;
        this.knowledgePointMapper = knowledgePointMapper;
        this.cache = cache;
    }

    public Page<QuestionListItem> page(int page, int size, Long subjectId, Long knowledgePointId, String type, String difficulty) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), 100);
        long version = cache.version("questions");
        String cacheKey = version < 0 ? null : questionPageCacheKey(version, current, pageSize,
                subjectId, knowledgePointId, type, difficulty);
        if (cacheKey != null) {
            Optional<QuestionPageCache> cached = cache.get(cacheKey, QuestionPageCache.class);
            if (cached.isPresent()) {
                QuestionPageCache value = cached.get();
                Page<QuestionListItem> result = new Page<>(value.current(), value.size(), value.total());
                result.setRecords(value.records() == null ? List.of() : value.records());
                return result;
            }
        }
        Page<Question> p = new Page<>(current, pageSize);
        QueryWrapper<Question> query = new QueryWrapper<>();
        query.eq(subjectId != null, "subject_id", subjectId)
             .eq(knowledgePointId != null, "knowledge_point_id", knowledgePointId)
             .eq(type != null && !type.isBlank(), "type", upper(type))
             .eq(difficulty != null && !difficulty.isBlank(), "difficulty", upper(difficulty))
             .eq("status", 1)
             .orderByDesc("id");
        Page<Question> source = mapper.selectPage(p, query);
        Page<QuestionListItem> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(source.getRecords().stream().map(QuestionListItem::of).toList());
        if (cacheKey != null) {
            cache.put(cacheKey, new QuestionPageCache(target.getCurrent(), target.getSize(), target.getTotal(), target.getRecords()), Duration.ofSeconds(90));
        }
        return target;
    }

    private String questionPageCacheKey(long version, long current, long size, Long subjectId,
                                        Long knowledgePointId, String type, String difficulty) {
        String query = current + "|" + size + "|" + subjectId + "|" + knowledgePointId + "|"
                + normalizeFilter(type) + "|" + normalizeFilter(difficulty);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(query.getBytes(StandardCharsets.UTF_8));
            return "online-exam:catalog:questions:v" + version + ":page:" + current + ":size:" + size + ":" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is required by every Java runtime; this fallback remains deterministic.
            return "online-exam:catalog:questions:v" + version + ":page:" + current + ":size:" + size + ":" + Integer.toHexString(query.hashCode());
        }
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? "*" : value.trim().toUpperCase(Locale.ROOT);
    }

    public QuestionDetailResponse getDetail(Long id, boolean revealAnswer) {
        Question question = get(id);
        List<QuestionOption> options = optionMapper.selectList(new QueryWrapper<QuestionOption>()
                .eq("question_id", id).orderByAsc("sort").orderByAsc("id"));
        return QuestionDetailResponse.of(question, options, revealAnswer);
    }

    public Question get(Long id) {
        Question question = mapper.selectById(id);
        if (question == null) throw new BusinessException(404, "题目不存在");
        return question;
    }

    @Transactional
    public QuestionDetailResponse create(QuestionRequest request, String username) {
        validate(request);
        if (subjectMapper.selectById(request.subjectId()) == null) throw new BusinessException(404, "学科不存在");
        KnowledgePoint kp = knowledgePointMapper.selectById(request.knowledgePointId());
        if (kp == null) throw new BusinessException(404, "知识点不存在");
        if (!request.subjectId().equals(kp.getSubjectId())) throw new BusinessException(400, "知识点不属于所选学科");

        SysUser creator = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).last("LIMIT 1"));
        if (creator == null) throw new BusinessException(401, "当前用户不存在");

        Question q = new Question();
        copy(request, q);
        q.setStatus(1);
        q.setCreatorId(creator.getId());
        mapper.insert(q);
        saveOptions(q.getId(), request.options(), q.getType());
        cache.invalidateAfterCommit("questions");
        return getDetail(q.getId(), true);
    }

    @Transactional
    public QuestionDetailResponse update(Long id, QuestionRequest request) {
        validate(request);
        if (subjectMapper.selectById(request.subjectId()) == null) throw new BusinessException(404, "学科不存在");
        KnowledgePoint kp = knowledgePointMapper.selectById(request.knowledgePointId());
        if (kp == null) throw new BusinessException(404, "知识点不存在");
        if (!request.subjectId().equals(kp.getSubjectId())) throw new BusinessException(400, "知识点不属于所选学科");

        Question q = get(id);
        copy(request, q);
        mapper.updateById(q);
        optionMapper.delete(new QueryWrapper<QuestionOption>().eq("question_id", id));
        saveOptions(id, request.options(), q.getType());
        cache.invalidateAfterCommit("questions");
        return getDetail(id, true);
    }

    @Transactional
    public void delete(Long id) {
        Question question = get(id);
        // Soft-disable instead of physical deletion so historical papers, answers,
        // practice records, favorites and notes keep valid references.
        question.setStatus(0);
        mapper.updateById(question);
        cache.invalidateAfterCommit("questions");
    }

    @Transactional
    public List<Long> createBatch(List<QuestionRequest> requests, String username) {
        if (requests == null || requests.isEmpty()) {
            throw new BusinessException(400, "请至少填写一道题目");
        }
        if (requests.size() > 200) {
            throw new BusinessException(400, "单次最多批量新增 200 道题目");
        }
        List<Long> ids = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            try {
                QuestionDetailResponse created = create(requests.get(i), username);
                ids.add(created.id());
            } catch (BusinessException ex) {
                throw new BusinessException(ex.getCode(), "第 " + (i + 1) + " 道题新增失败：" + ex.getMessage());
            }
        }
        return ids;
    }

    @Transactional
    public int deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(400, "请先勾选要删除的题目");
        }
        List<Long> uniqueIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (uniqueIds.isEmpty()) throw new BusinessException(400, "题目 ID 列表不能为空");
        if (uniqueIds.size() > 500) throw new BusinessException(400, "单次最多批量删除 500 道题目");

        List<Question> questions = mapper.selectBatchIds(uniqueIds);
        if (questions.size() != uniqueIds.size()) {
            throw new BusinessException(404, "部分题目不存在，请刷新题库后重试");
        }
        int disabled = 0;
        for (Question question : questions) {
            if (!Integer.valueOf(0).equals(question.getStatus())) {
                question.setStatus(0);
                mapper.updateById(question);
                disabled++;
            }
        }
        if (disabled > 0) cache.invalidateAfterCommit("questions");
        return disabled;
    }

    private void validate(QuestionRequest request) {
        String type = upper(request.type());
        String difficulty = upper(request.difficulty());
        if (!TYPES.contains(type)) throw new BusinessException(400, "不支持的题型: " + request.type());
        if (!DIFFICULTIES.contains(difficulty)) throw new BusinessException(400, "不支持的难度: " + request.difficulty());

        boolean choice = type.equals("SINGLE_CHOICE") || type.equals("MULTIPLE_CHOICE") || type.equals("TRUE_FALSE");
        List<QuestionRequest.OptionRequest> options = request.options() == null ? List.of() : request.options();
        if (choice && options.size() < 2) throw new BusinessException(400, "选择类题目至少需要两个选项");
        if (choice && options.size() > 6) throw new BusinessException(400, "选择类题目最多支持六个选项（A-F），以兼容 Excel 导入导出");
        if (type.equals("TRUE_FALSE") && options.size() != 2) throw new BusinessException(400, "判断题必须且只能提供两个选项");
        if (choice) {
            long correct = options.stream().filter(QuestionRequest.OptionRequest::correct).count();
            if (type.equals("SINGLE_CHOICE") && correct != 1) throw new BusinessException(400, "单选题必须且只能有一个正确选项");
            if (type.equals("MULTIPLE_CHOICE") && correct < 1) throw new BusinessException(400, "多选题至少需要一个正确选项");
            if (type.equals("TRUE_FALSE") && correct != 1) throw new BusinessException(400, "判断题必须有一个正确选项");
        } else if (request.answerConfig() == null || request.answerConfig().isBlank()) {
            throw new BusinessException(400, "该题型必须提供答案配置");
        }
        if (request.defaultScore().signum() <= 0) throw new BusinessException(400, "题目分值必须大于0");
    }

    private void saveOptions(Long questionId, List<QuestionRequest.OptionRequest> requestOptions, String type) {
        if (requestOptions == null || requestOptions.isEmpty()) return;
        Set<String> keys = new HashSet<>();
        for (QuestionRequest.OptionRequest request : requestOptions) {
            String key = request.optionKey().trim().toUpperCase(Locale.ROOT);
            if (!keys.add(key)) throw new BusinessException(400, "选项标识重复: " + key);
        }
        for (int i = 0; i < requestOptions.size(); i++) {
            QuestionRequest.OptionRequest request = requestOptions.get(i);
            QuestionOption option = new QuestionOption();
            option.setQuestionId(questionId);
            option.setOptionKey(request.optionKey().trim().toUpperCase(Locale.ROOT));
            option.setOptionContent(request.optionContent().trim());
            option.setSort(request.sort() == null ? i + 1 : request.sort());
            option.setIsCorrect(Boolean.TRUE.equals(request.correct()) ? 1 : 0);
            optionMapper.insert(option);
        }
    }

    private void copy(QuestionRequest r, Question q) {
        q.setSubjectId(r.subjectId());
        q.setKnowledgePointId(r.knowledgePointId());
        q.setType(upper(r.type()));
        q.setContent(r.content().trim());
        q.setAnswerConfig(r.answerConfig());
        q.setAnalysis(r.analysis());
        q.setDifficulty(upper(r.difficulty()));
        q.setDifficultyValue(r.difficultyValue());
        q.setDefaultScore(r.defaultScore());
        q.setTags(r.tags());
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
