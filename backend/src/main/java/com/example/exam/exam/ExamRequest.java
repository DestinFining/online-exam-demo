package com.example.exam.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record ExamRequest(
        @NotBlank(message = "考试名称不能为空") String name,
        @NotNull(message = "试卷不能为空") Long paperId,
        @NotNull(message = "开始时间不能为空") LocalDateTime startTime,
        @NotNull(message = "结束时间不能为空") LocalDateTime endTime,
        @NotNull @Min(value = 1, message = "考试时长必须大于0") @Max(value = 1440, message = "考试时长不能超过1440分钟") Integer duration,
        @NotNull @Min(value = 1, message = "最大考试次数必须大于0") Integer maxAttempts,
        @NotBlank(message = "考生范围不能为空") String candidateScope,
        @Valid @Size(max = 10000, message = "指定考生数量过多") List<Long> candidateUserIds
) {}
