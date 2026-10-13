package com.example.exam.grade;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GradeAnswerView(
        Long answerId,
        Long paperQuestionId,
        Long questionId,
        Integer questionNo,
        String type,
        String content,
        String answerContent,
        String referenceAnswer,
        BigDecimal maxScore,
        BigDecimal autoScore,
        BigDecimal manualScore,
        BigDecimal finalScore,
        Integer isCorrect,
        String teacherComment,
        LocalDateTime gradedAt
) {}
