package com.example.exam.grade;

import com.example.exam.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/grades")
public class GradeController {
    private final GradeService service;

    public GradeController(GradeService service) { this.service = service; }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('grade:view')")
    public ApiResponse<?> my(Authentication authentication) {
        return ApiResponse.success(service.myResults(authentication.getName()));
    }

    @GetMapping("/exam/{examId}")
    @PreAuthorize("hasAuthority('grade:view')")
    public ApiResponse<?> exam(@PathVariable Long examId, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        return ApiResponse.success(service.examSubmissions(examId, authentication.getName(), admin));
    }

    @GetMapping("/{attemptId}/answers")
    @PreAuthorize("hasAuthority('grade:view')")
    public ApiResponse<?> answers(@PathVariable Long attemptId, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        return ApiResponse.success(service.detail(attemptId, authentication.getName(), admin));
    }

    @PutMapping("/answers/{answerId}")
    @PreAuthorize("hasAuthority('grade:judge')")
    public ApiResponse<?> gradeAnswer(@PathVariable Long answerId,
                                      @Valid @RequestBody GradeAnswerRequest request,
                                      Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        return ApiResponse.success("主观题评分已保存", service.gradeAnswer(answerId, request, authentication.getName(), admin));
    }

    @PostMapping("/{attemptId}/finalize")
    @PreAuthorize("hasAuthority('grade:judge')")
    public ApiResponse<?> finalizeAttempt(@PathVariable Long attemptId, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        return ApiResponse.success("成绩结算成功", service.finalizeAttempt(attemptId, authentication.getName(), admin));
    }

    @PostMapping("/exam/{examId}/publish-all")
    @PreAuthorize("hasAuthority('grade:publish')")
    public ApiResponse<?> publishAll(@PathVariable Long examId, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        return ApiResponse.success("本场考试成绩批量发布完成",
                service.publishAllForExam(examId, authentication.getName(), admin));
    }

    @PostMapping("/{attemptId}/publish")
    @PreAuthorize("hasAuthority('grade:publish')")
    public ApiResponse<?> publish(@PathVariable Long attemptId, Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> "role:manage".equals(a.getAuthority()));
        return ApiResponse.success("成绩发布成功", service.publishAttempt(attemptId, authentication.getName(), admin));
    }
}
