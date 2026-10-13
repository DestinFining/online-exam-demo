package com.example.exam.grade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Teacher-facing score summary deliberately excludes the student's name and username. */
public record GradeSummary(
        Long attemptId,
        Long examId,
        String candidateLabel,
        Integer attemptNo,
        String status,
        BigDecimal objectiveScore,
        BigDecimal subjectiveScore,
        BigDecimal totalScore,
        Integer cheatCount,
        LocalDateTime startTime,
        LocalDateTime submitTime
) {}
