package com.example.exam.question;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Row shape of the paged question list.
 *
 * <p>The analysis text is exposed only as the {@code hasAnalysis} flag: the list
 * endpoint is readable by every authenticated user, so returning the analysis
 * here would hand students the explanation that the detail endpoint withholds.
 * {@code createdAt} is safe to expose and lets the UI show when a question was
 * added.
 */
public record QuestionListItem(
        Long id,
        Long subjectId,
        Long knowledgePointId,
        String type,
        String content,
        String difficulty,
        BigDecimal difficultyValue,
        BigDecimal defaultScore,
        String tags,
        Boolean hasAnalysis,
        LocalDateTime createdAt
) {
    public static QuestionListItem of(Question q) {
        String analysis = q.getAnalysis();
        return new QuestionListItem(q.getId(), q.getSubjectId(), q.getKnowledgePointId(), q.getType(), q.getContent(),
                q.getDifficulty(), q.getDifficultyValue(), q.getDefaultScore(), q.getTags(),
                analysis != null && !analysis.isBlank(), q.getCreatedAt());
    }
}
