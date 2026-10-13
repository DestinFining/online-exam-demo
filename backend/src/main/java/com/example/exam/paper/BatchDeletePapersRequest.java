package com.example.exam.paper;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchDeletePapersRequest(
        @NotEmpty(message = "请至少选择一份试卷")
        @Size(max = 100, message = "单次最多删除 100 份试卷")
        List<@NotNull @Positive Long> ids
) {}
