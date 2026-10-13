package com.example.exam.wrongquestion;

import com.example.exam.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wrong-questions")
public class WrongQuestionController {
    private final WrongQuestionService service;

    public WrongQuestionController(WrongQuestionService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('wrong-question:view')")
    public ApiResponse<?> list(Authentication authentication) {
        return ApiResponse.success(service.myList(authentication.getName()));
    }

    @PostMapping("/{questionId}/redo-correct")
    @PreAuthorize("hasAuthority('wrong-question:update')")
    public ApiResponse<?> redoCorrect(@PathVariable Long questionId, Authentication authentication) {
        return ApiResponse.success("错题掌握状态已更新", service.markCorrect(questionId, authentication.getName()));
    }

    @PostMapping("/{questionId}/mark-wrong")
    @PreAuthorize("hasAuthority('wrong-question:update')")
    public ApiResponse<?> markWrong(@PathVariable Long questionId, Authentication authentication) {
        return ApiResponse.success("已加入错题本", service.markWrong(questionId, authentication.getName()));
    }
}
