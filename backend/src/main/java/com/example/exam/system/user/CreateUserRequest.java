package com.example.exam.system.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度须为 3-50 个字符")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "用户名仅允许字母、数字、点、下划线和连字符")
        String username,
        @NotBlank(message = "初始密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度须为 8-72 个字符")
        String password,
        @NotBlank(message = "姓名/昵称不能为空") @Size(max = 100) String nickname,
        @Email(message = "邮箱格式不正确") @Size(max = 150) String email,
        @Size(max = 30) String phone,
        @NotEmpty(message = "请至少分配一个角色") List<String> roleCodes,
        Integer status
) {}
