package com.example.exam.system.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.exam.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {
    private final UserAdminService service;
    public UserAdminController(UserAdminService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('user:view')")
    public ApiResponse<Page<UserAdminResponse>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.success(service.page(page, size, keyword, roleCode, status));
    }

    @GetMapping("/role-options")
    @PreAuthorize("hasAuthority('user:view')")
    public ApiResponse<List<Map<String, String>>> roles() { return ApiResponse.success(service.roleOptions()); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:view')")
    public ApiResponse<UserAdminResponse> get(@PathVariable Long id) { return ApiResponse.success(service.get(id)); }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public ApiResponse<UserAdminResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("用户创建成功", service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    public ApiResponse<UserAdminResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request, Authentication authentication) {
        return ApiResponse.success("用户更新成功", service.update(id, request, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:disable')")
    public ApiResponse<Map<String, Object>> disable(@PathVariable Long id, Authentication authentication) {
        service.disable(id, authentication.getName());
        return ApiResponse.success("用户已停用（保留历史答卷和成绩）", Map.of("id", id, "status", 0));
    }
}
