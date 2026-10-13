package com.example.exam.question;

import com.example.exam.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {
    private final QuestionService service;

    public QuestionController(QuestionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long knowledgePointId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty) {
        return ApiResponse.success(service.page(page, size, subjectId, knowledgePointId, type, difficulty));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<?> get(@PathVariable Long id, Authentication authentication) {
        boolean reveal = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("question:create") || a.getAuthority().equals("question:update"));
        return ApiResponse.success(service.getDetail(id, reveal));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('question:create')")
    public ApiResponse<?> create(@Valid @RequestBody QuestionRequest request, Authentication authentication) {
        return ApiResponse.success("创建成功", service.create(request, authentication.getName()));
    }

    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('question:create')")
    public ApiResponse<?> createBatch(@Valid @RequestBody BatchQuestionRequest request,
                                      Authentication authentication) {
        var ids = service.createBatch(request.questions(), authentication.getName());
        return ApiResponse.success("批量新增成功", Map.of("count", ids.size(), "ids", ids));
    }

    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('question:delete')")
    public ApiResponse<?> deleteBatch(@Valid @RequestBody BatchDeleteQuestionsRequest request) {
        int count = service.deleteBatch(request.ids());
        return ApiResponse.success("批量移除成功", Map.of("deleted", count));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('question:update')")
    public ApiResponse<?> update(@PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        return ApiResponse.success("更新成功", service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('question:delete')")
    public ApiResponse<?> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(Map.of("deleted", true));
    }
}
