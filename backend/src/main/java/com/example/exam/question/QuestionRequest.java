package com.example.exam.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record QuestionRequest(
        @NotNull(message = "学科不能为空") Long subjectId,
        @NotNull(message = "知识点不能为空") Long knowledgePointId,
        @NotBlank(message = "题型不能为空") String type,
        @NotBlank(message = "题干不能为空") @Size(max = 50000, message = "题干过长") String content,
        String answerConfig,
        String analysis,
        @NotBlank(message = "难度不能为空") String difficulty,
        @NotNull @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") BigDecimal difficultyValue,
        @NotNull @Positive BigDecimal defaultScore,
        String tags,
        @Valid List<OptionRequest> options
) {
    public record OptionRequest(
            @NotBlank(message = "选项标识不能为空") String optionKey,
            @NotBlank(message = "选项内容不能为空") String optionContent,
            Integer sort,
            @NotNull Boolean correct
    ) {}
}
