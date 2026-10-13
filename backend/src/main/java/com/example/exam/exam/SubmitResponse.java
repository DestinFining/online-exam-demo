package com.example.exam.exam;

import java.math.BigDecimal;

public record SubmitResponse(
        Long attemptId,
        String status,
        BigDecimal objectiveScore,
        BigDecimal subjectiveScore,
        BigDecimal totalScore,
        boolean manualGradingRequired
) {}
