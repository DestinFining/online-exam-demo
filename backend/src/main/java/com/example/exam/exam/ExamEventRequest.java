package com.example.exam.exam;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExamEventRequest(
        @NotNull Long attemptId,
        @NotBlank String eventType,
        String clientInfo,
        Object metadata
) {}
