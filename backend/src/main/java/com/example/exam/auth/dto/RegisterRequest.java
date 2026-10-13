package com.example.exam.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 4, max = 50, message = "用户名长度必须为4-50个字符")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 72, message = "密码长度必须为6-72个字符")
        String password,
        @NotBlank(message = "确认密码不能为空")
        String confirmPassword,
        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称不能超过50个字符")
        String nickname,
        String email,
        String phone
) {}
