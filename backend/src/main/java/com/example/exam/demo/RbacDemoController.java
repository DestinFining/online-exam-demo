package com.example.exam.demo;

import com.example.exam.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/demo")
public class RbacDemoController {

    @GetMapping("/student")
    @PreAuthorize("hasAuthority('practice:start')")
    public ApiResponse<?> student() {
        return ApiResponse.success(Map.of("message", "学生权限验证通过"));
    }

    @GetMapping("/teacher")
    @PreAuthorize("hasAuthority('question:create')")
    public ApiResponse<?> teacher() {
        return ApiResponse.success(Map.of("message", "教师权限验证通过"));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('role:manage')")
    public ApiResponse<?> admin() {
        return ApiResponse.success(Map.of("message", "管理员权限验证通过"));
    }
}
