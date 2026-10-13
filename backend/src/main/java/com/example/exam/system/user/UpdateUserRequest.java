package com.example.exam.system.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateUserRequest(
        @Size(max = 100) String nickname,
        @Email(message = "邮箱格式不正确") @Size(max = 150) String email,
        @Size(max = 30) String phone,
        @Size(min = 8, max = 72, message = "新密码长度须为 8-72 个字符") String password,
        List<String> roleCodes,
        Integer status
) {}
