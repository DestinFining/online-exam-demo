package com.example.exam.exam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ExamAttemptDetail(
        Exam exam,
        ExamAttempt attempt,
        LocalDateTime effectiveEndTime,
        List<ExamResponse.PublicQuestion> questions,
        boolean canSubmit
) {}
