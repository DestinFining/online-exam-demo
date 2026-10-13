package com.example.exam.paper;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record PaperRequest(
        @NotBlank(message = "试卷名称不能为空") String name,
        @NotNull(message = "学科不能为空") Long subjectId,
        @NotBlank(message = "试卷类型不能为空") String type,
        @NotNull @Positive(message = "考试时长必须大于0") Integer duration,
        @Valid List<FixedQuestionItem> questions,
        @Valid RuleConfig ruleConfig
) {
    public record FixedQuestionItem(
            @NotNull Long questionId,
            @NotNull @Positive BigDecimal score
    ) {}

    public record RuleConfig(
            @NotNull @Positive BigDecimal totalScore,
            @Valid @NotNull List<TypeRule> types,
            Map<String, BigDecimal> difficulty,
            List<Long> knowledgePointIds
    ) {}

    public record TypeRule(
            @NotBlank String type,
            @NotNull @Min(1) Integer count,
            @NotNull @Positive BigDecimal score
    ) {}
}
