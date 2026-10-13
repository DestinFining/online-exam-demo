package com.example.exam.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.config.RedisJsonCache;
import com.example.exam.subject.SubjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class KnowledgePointService {
    private static final String CACHE_NAMESPACE = "knowledge-points";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<KnowledgePoint>> KNOWLEDGE_LIST_TYPE = new TypeReference<>() {};

    private final KnowledgePointMapper mapper;
    private final SubjectService subjectService;
    private final RedisJsonCache cache;

    public KnowledgePointService(KnowledgePointMapper mapper, SubjectService subjectService, RedisJsonCache cache) {
        this.mapper = mapper;
        this.subjectService = subjectService;
        this.cache = cache;
    }

    public List<KnowledgePoint> list(Long subjectId, Boolean enabledOnly) {
        if (subjectId != null) subjectService.get(subjectId);
        boolean enabled = Boolean.TRUE.equals(enabledOnly);
        long version = cache.version(CACHE_NAMESPACE);
        String cacheKey = version < 0 ? null : "online-exam:catalog:knowledge-points:v" + version
                + ":subject:" + (subjectId == null ? "all" : subjectId)
                + ":enabled:" + enabled;
        if (cacheKey != null) {
            Optional<List<KnowledgePoint>> cached = cache.get(cacheKey, KNOWLEDGE_LIST_TYPE);
            if (cached.isPresent()) return cached.get();
        }
        QueryWrapper<KnowledgePoint> query = new QueryWrapper<>();
        query.eq(subjectId != null, "subject_id", subjectId);
        query.eq(enabled, "status", 1);
        query.orderByAsc("sort").orderByAsc("id");
        List<KnowledgePoint> points = mapper.selectList(query);
        if (cacheKey != null) cache.put(cacheKey, points, CACHE_TTL);
        return points;
    }

    public KnowledgePoint get(Long id) {
        long version = cache.version(CACHE_NAMESPACE);
        String cacheKey = version < 0 ? null : "online-exam:catalog:knowledge-points:v" + version + ":id:" + id;
        if (cacheKey != null) {
            Optional<KnowledgePoint> cached = cache.get(cacheKey, KnowledgePoint.class);
            if (cached.isPresent()) return cached.get();
        }
        KnowledgePoint point = mapper.selectById(id);
        if (point == null) throw new BusinessException(404, "知识点不存在");
        if (cacheKey != null) cache.put(cacheKey, point, CACHE_TTL);
        return point;
    }

    @Transactional
    public KnowledgePoint create(KnowledgePointRequest request) {
        validateReferences(request.subjectId(), request.parentId());
        String name = request.name().trim();
        if (existsName(request.subjectId(), request.parentId(), name, null)) {
            throw new BusinessException(409, "同一层级下知识点名称已存在");
        }
        KnowledgePoint point = new KnowledgePoint();
        copy(request, point);
        point.setName(name);
        point.setStatus(request.status() == null ? 1 : request.status());
        point.setSort(request.sort() == null ? 0 : request.sort());
        mapper.insert(point);
        cache.invalidateAfterCommit(CACHE_NAMESPACE);
        return point;
    }

    @Transactional
    public KnowledgePoint update(Long id, KnowledgePointRequest request) {
        KnowledgePoint point = get(id);
        validateReferences(request.subjectId(), request.parentId());
        if (id.equals(request.parentId())) throw new BusinessException(400, "父知识点不能是自身");
        String name = request.name().trim();
        if (existsName(request.subjectId(), request.parentId(), name, id)) {
            throw new BusinessException(409, "同一层级下知识点名称已存在");
        }
        copy(request, point);
        point.setName(name);
        if (request.status() != null) point.setStatus(request.status());
        if (request.sort() != null) point.setSort(request.sort());
        mapper.updateById(point);
        cache.invalidateAfterCommit(CACHE_NAMESPACE);
        return point;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        long children = mapper.selectCount(new QueryWrapper<KnowledgePoint>().eq("parent_id", id));
        if (children > 0) throw new BusinessException(409, "该知识点存在子知识点，不能直接删除");
        try {
            mapper.deleteById(id);
            cache.invalidateAfterCommit(CACHE_NAMESPACE);
        } catch (Exception ex) {
            throw new BusinessException(409, "该知识点仍被题库数据引用，建议停用而不要删除");
        }
    }

    private void validateReferences(Long subjectId, Long parentId) {
        subjectService.get(subjectId);
        if (parentId != null) {
            KnowledgePoint parent = mapper.selectById(parentId);
            if (parent == null) throw new BusinessException(404, "父知识点不存在");
            if (!subjectId.equals(parent.getSubjectId())) {
                throw new BusinessException(400, "父知识点必须属于同一学科");
            }
        }
    }

    private boolean existsName(Long subjectId, Long parentId, String name, Long excludeId) {
        QueryWrapper<KnowledgePoint> query = new QueryWrapper<>();
        query.eq("subject_id", subjectId)
             .eq("name", name)
             .isNull(parentId == null, "parent_id")
             .eq(parentId != null, "parent_id", parentId)
             .ne(excludeId != null, "id", excludeId);
        return mapper.selectCount(query) > 0;
    }

    private void copy(KnowledgePointRequest r, KnowledgePoint p) {
        p.setSubjectId(r.subjectId());
        p.setParentId(r.parentId());
        p.setDescription(blankToNull(r.description()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
