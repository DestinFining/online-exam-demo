package com.example.exam.exam;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.exam.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exams")
public class ExamController {
    private final ExamService service;

    public ExamController(ExamService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('exam:view')")
    public ApiResponse<Page<Exam>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            Authentication authentication) {
        boolean teacherOrAdmin = authentication.getAuthorities().stream().anyMatch(a ->
                "exam:create".equals(a.getAuthority()) || "exam:update".equals(a.getAuthority()));
        return ApiResponse.success(service.page(page, size, status, authentication.getName(), teacherOrAdmin));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('exam:view')")
    public ApiResponse<?> get(@PathVariable Long id, Authentication authentication) {
        Exam exam = service.get(id);
        boolean teacherOrAdmin = authentication.getAuthorities().stream().anyMatch(a ->
                "exam:create".equals(a.getAuthority()) || "exam:update".equals(a.getAuthority()));
        if (!teacherOrAdmin && !"PUBLISHED".equals(exam.getStatus())) {
            throw new com.example.exam.common.exception.BusinessException(404, "考试不存在");
        }
        return ApiResponse.success(service.overview(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('exam:create')")
    public ApiResponse<Exam> create(@Valid @RequestBody ExamRequest request, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        return ApiResponse.success("创建考试成功", service.create(request, authentication.getName(), admin));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('exam:update')")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ExamRequest request,
                                    Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        service.update(id, request, authentication.getName(), admin);
        return ApiResponse.success("更新考试成功", null);
    }

    @GetMapping("/{id}/candidates")
    @PreAuthorize("hasAuthority('exam:update')")
    public ApiResponse<List<Long>> candidateIds(@PathVariable Long id, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        return ApiResponse.success(service.candidateIds(id, authentication.getName(), admin));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('exam:update')")
    public ApiResponse<Void> deleteEnded(@PathVariable Long id, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        service.deleteEnded(id, authentication.getName(), admin);
        return ApiResponse.success("考试已归档，历史答卷和成绩已保留", null);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('exam:publish')")
    public ApiResponse<Void> publish(@PathVariable Long id, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        service.publish(id, authentication.getName(), admin);
        return ApiResponse.success("发布考试成功", null);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('exam:join')")
    public ApiResponse<ExamResponse.AttemptResponse> start(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success("考试开始", service.start(id, authentication.getName()));
    }

    @GetMapping("/{id}/attempt")
    @PreAuthorize("hasAuthority('exam:join')")
    public ApiResponse<ExamAttemptDetail> resume(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(service.resume(id, authentication.getName()));
    }

    @PostMapping("/{id}/save")
    @PreAuthorize("hasAuthority('exam:save')")
    public ApiResponse<ExamAttemptDetail> save(@PathVariable Long id, @Valid @RequestBody SaveAnswerRequest request,
                                                Authentication authentication) {
        return ApiResponse.success("答案已保存", service.save(id, request, authentication.getName()));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('exam:submit')")
    public ApiResponse<SubmitResponse> submit(@PathVariable Long id,
                                              @RequestParam Long attemptId,
                                              @RequestBody(required = false) List<SaveAnswerRequest.AnswerItem> answers,
                                              Authentication authentication) {
        return ApiResponse.success("试卷已提交", service.submit(id, attemptId, answers, authentication.getName()));
    }

    @PostMapping("/{id}/events")
    @PreAuthorize("hasAuthority('exam:save')")
    public ApiResponse<Void> event(@PathVariable Long id, @Valid @RequestBody ExamEventRequest request,
                                   Authentication authentication) {
        service.recordEvent(id, request, authentication.getName());
        return ApiResponse.success("事件已记录", null);
    }
}
