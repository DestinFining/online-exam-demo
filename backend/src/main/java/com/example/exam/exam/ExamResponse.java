package com.example.exam.exam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ExamResponse(
        Exam exam,
        String paperName,
        BigDecimal totalScore,
        Integer questionCount
) {
    public record AttemptResponse(
            Exam exam,
            ExamAttempt attempt,
            LocalDateTime effectiveEndTime,
            List<PublicQuestion> questions
    ) {}

    public record PublicQuestion(
            Long paperQuestionId,
            Integer questionNo,
            BigDecimal score,
            String content,
            String type,
            String difficulty,
            List<PublicOption> options,
            String savedAnswer
    ) {}

    public record PublicOption(
            String optionKey,
            String optionContent,
            Integer sort
    ) {}
}
