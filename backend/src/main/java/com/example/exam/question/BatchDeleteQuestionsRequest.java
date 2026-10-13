package com.example.exam.question;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchDeleteQuestionsRequest(
        @NotEmpty(message = "请先勾选要删除的题目")
        @Size(max = 500, message = "单次最多批量删除 500 道题目")
        List<@NotNull Long> ids
) {}
