package com.example.exam.paper;

import java.math.BigDecimal;
import java.util.List;

public record PaperResponse(
        Paper paper,
        List<QuestionSnapshot> questions
) {
    public record QuestionSnapshot(
            Long id,
            Long questionId,
            Integer questionNo,
            BigDecimal score,
            String content,
            String type,
            String analysis,
            String difficulty,
            String answerConfig,
            List<OptionSnapshot> options
    ) {}

    public record OptionSnapshot(
            String optionKey,
            String optionContent,
            Integer sort,
            Boolean correct
    ) {}
}
