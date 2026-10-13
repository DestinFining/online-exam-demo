package com.example.exam.question;

import java.math.BigDecimal;
import java.util.List;

public record QuestionDetailResponse(
        Long id,
        Long subjectId,
        Long knowledgePointId,
        String type,
        String content,
        String analysis,
        String difficulty,
        BigDecimal difficultyValue,
        BigDecimal defaultScore,
        String tags,
        String answerConfig,
        List<OptionResponse> options
) {
    public record OptionResponse(
            Long id,
            String optionKey,
            String optionContent,
            Integer sort,
            Boolean correct
    ) {}

    public static QuestionDetailResponse of(Question question, List<QuestionOption> options, boolean revealAnswer) {
        List<OptionResponse> responseOptions = options.stream()
                .map(o -> new OptionResponse(
                        o.getId(), o.getOptionKey(), o.getOptionContent(), o.getSort(),
                        revealAnswer ? Integer.valueOf(1).equals(o.getIsCorrect()) : null))
                .toList();
        return new QuestionDetailResponse(
                question.getId(), question.getSubjectId(), question.getKnowledgePointId(), question.getType(),
                question.getContent(), question.getAnalysis(), question.getDifficulty(),
                question.getDifficultyValue(), question.getDefaultScore(), question.getTags(),
                revealAnswer ? question.getAnswerConfig() : null, responseOptions);
    }
}
