package com.example.exam.knowledge;

import com.example.exam.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge-points")
public class KnowledgePointController {
    private final KnowledgePointService service;

    public KnowledgePointController(KnowledgePointService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> list(@RequestParam(required = false) Long subjectId,
                               @RequestParam(defaultValue = "true") boolean enabledOnly) {
        return ApiResponse.success(service.list(subjectId, enabledOnly));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('knowledge:manage')")
    public ApiResponse<?> create(@Valid @RequestBody KnowledgePointRequest request) {
        return ApiResponse.success("创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody KnowledgePointRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('knowledge:manage')")
    public ApiResponse<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
