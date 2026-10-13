package com.example.exam.auth.dto;

import java.util.List;

public record LoginResponse(
        Long userId,
        String username,
        String nickname,
        List<String> roles,
        String accessToken,
        long expiresIn
) {}
