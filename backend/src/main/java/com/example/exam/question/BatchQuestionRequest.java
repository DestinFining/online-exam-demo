package com.example.exam.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchQuestionRequest(
        @NotEmpty(message = "请至少提供一道题目")
        @Size(max = 200, message = "单次最多批量新增 200 道题目")
        List<@NotNull @Valid QuestionRequest> questions
) {}
