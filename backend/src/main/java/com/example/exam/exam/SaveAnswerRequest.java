package com.example.exam.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveAnswerRequest(
        @NotNull(message = "attemptId不能为空") Long attemptId,
        @NotNull(message = "答题记录不能为空") @Valid List<AnswerItem> answers,
        Long clientVersion
) {
    public record AnswerItem(
            @NotNull Long paperQuestionId,
            Object answerContent
    ) {}
}
