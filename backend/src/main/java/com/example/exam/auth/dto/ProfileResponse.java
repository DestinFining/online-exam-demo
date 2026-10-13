package com.example.exam.auth.dto;

import java.util.List;

public record ProfileResponse(
        Long userId,
        String username,
        String nickname,
        String email,
        String phone,
        List<String> roles
) {}
