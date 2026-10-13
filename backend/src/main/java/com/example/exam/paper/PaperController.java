package com.example.exam.paper;

import com.example.exam.common.response.ApiResponse;
import com.example.exam.common.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/papers")
public class PaperController {
    private final PaperService service;

    public PaperController(PaperService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('paper:view')")
    public ApiResponse<?> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        boolean teacherOrAdmin = authenticationHasAny("paper:create", "paper:generate", authentication);
        String visibleStatus = teacherOrAdmin ? status : "PUBLISHED";
        return ApiResponse.success(service.page(page, size, subjectId, type, visibleStatus));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('paper:view')")
    public ApiResponse<?> get(@PathVariable Long id, Authentication authentication) {
        boolean reveal = authenticationHasAny("paper:create", "paper:generate", authentication);
        Paper paper = service.get(id);
        if (!reveal && !"PUBLISHED".equals(paper.getStatus())) {
            throw new BusinessException(404, "试卷不存在");
        }
        return ApiResponse.success(service.getDetail(id, reveal));
    }

    private boolean authenticationHasAny(String a, String b, Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(x ->
                a.equals(x.getAuthority()) || b.equals(x.getAuthority()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('paper:create')")
    public ApiResponse<?> create(@Valid @RequestBody PaperRequest request, Authentication authentication) {
        return ApiResponse.success("创建成功", service.create(request, authentication.getName()));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAuthority('paper:generate')")
    public ApiResponse<?> generate(@Valid @RequestBody PaperRequest request, Authentication authentication) {
        if (!"RULE".equalsIgnoreCase(request.type())) {
            throw new com.example.exam.common.exception.BusinessException(400, "动态组卷接口的type必须是RULE");
        }
        return ApiResponse.success("动态组卷成功", service.create(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('paper:update')")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody PaperRequest request,
                                 Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        return ApiResponse.success("更新成功", service.update(id, request, authentication.getName(), admin));
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('paper:update')")
    public ApiResponse<?> deleteBatch(@Valid @RequestBody BatchDeletePapersRequest request,
                                      Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        int deleted = service.deleteBatch(request.ids(), authentication.getName(), admin);
        return ApiResponse.success("批量删除成功", java.util.Map.of("deleted", deleted));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('paper:publish')")
    public ApiResponse<?> publish(@PathVariable Long id) {
        service.publish(id);
        return ApiResponse.success("发布成功", null);
    }
}
