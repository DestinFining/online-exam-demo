package com.example.exam.system.user;

import java.time.LocalDateTime;
import java.util.List;

public record UserAdminResponse(
        Long id, String username, String nickname, String email, String phone, Integer status,
        LocalDateTime lastLoginAt, LocalDateTime createdAt, List<String> roles
) {}
