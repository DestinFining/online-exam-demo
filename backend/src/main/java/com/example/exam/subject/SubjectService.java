package com.example.exam.subject;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.config.RedisJsonCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class SubjectService {
    private static final String CACHE_NAMESPACE = "subjects";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<Subject>> SUBJECT_LIST_TYPE = new TypeReference<>() {};

    private final SubjectMapper mapper;
    private final RedisJsonCache cache;

    public SubjectService(SubjectMapper mapper, RedisJsonCache cache) {
        this.mapper = mapper;
        this.cache = cache;
    }

    public List<Subject> list(Boolean enabledOnly) {
        boolean enabled = Boolean.TRUE.equals(enabledOnly);
        long version = cache.version(CACHE_NAMESPACE);
        String cacheKey = version < 0 ? null : "online-exam:catalog:subjects:v" + version + ":enabled:" + enabled;
        if (cacheKey != null) {
            Optional<List<Subject>> cached = cache.get(cacheKey, SUBJECT_LIST_TYPE);
            if (cached.isPresent()) return cached.get();
        }
        QueryWrapper<Subject> query = new QueryWrapper<>();
        if (enabled) query.eq("status", 1);
        query.orderByAsc("sort").orderByAsc("id");
        List<Subject> subjects = mapper.selectList(query);
        if (cacheKey != null) cache.put(cacheKey, subjects, CACHE_TTL);
        return subjects;
    }

    public Subject get(Long id) {
        long version = cache.version(CACHE_NAMESPACE);
        String cacheKey = version < 0 ? null : "online-exam:catalog:subjects:v" + version + ":id:" + id;
        if (cacheKey != null) {
            Optional<Subject> cached = cache.get(cacheKey, Subject.class);
            if (cached.isPresent()) return cached.get();
        }
        Subject subject = mapper.selectById(id);
        if (subject == null) throw new BusinessException(404, "学科不存在");
        if (cacheKey != null) cache.put(cacheKey, subject, CACHE_TTL);
        return subject;
    }

    @Transactional
    public Subject create(SubjectRequest request) {
        String name = request.name().trim();
        if (mapper.selectCount(new QueryWrapper<Subject>().eq("name", name)) > 0) {
            throw new BusinessException(409, "学科名称已存在");
        }
        Subject subject = new Subject();
        subject.setName(name);
        subject.setDescription(blankToNull(request.description()));
        subject.setStatus(request.status() == null ? 1 : request.status());
        subject.setSort(request.sort() == null ? 0 : request.sort());
        mapper.insert(subject);
        cache.invalidateAfterCommit(CACHE_NAMESPACE);
        return subject;
    }

    @Transactional
    public Subject update(Long id, SubjectRequest request) {
        Subject subject = get(id);
        String name = request.name().trim();
        if (mapper.selectCount(new QueryWrapper<Subject>()
                .eq("name", name).ne("id", id)) > 0) {
            throw new BusinessException(409, "学科名称已存在");
        }
        subject.setName(name);
        subject.setDescription(blankToNull(request.description()));
        if (request.status() != null) subject.setStatus(request.status());
        if (request.sort() != null) subject.setSort(request.sort());
        mapper.updateById(subject);
        cache.invalidateAfterCommit(CACHE_NAMESPACE);
        return subject;
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        if (mapper.selectCount(new QueryWrapper<Subject>().eq("id", id)) == 0) {
            throw new BusinessException(404, "学科不存在");
        }
        try {
            mapper.deleteById(id);
            cache.invalidateAfterCommit(CACHE_NAMESPACE);
        } catch (Exception ex) {
            throw new BusinessException(409, "该学科仍被其他数据引用，建议停用而不要删除");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
