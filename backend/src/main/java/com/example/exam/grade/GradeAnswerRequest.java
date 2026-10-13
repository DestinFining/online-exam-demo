package com.example.exam.grade;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record GradeAnswerRequest(
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal score,
        String comment
) {}
