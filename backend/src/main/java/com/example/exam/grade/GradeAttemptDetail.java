package com.example.exam.grade;

import java.util.List;

public record GradeAttemptDetail(
        GradeSummary summary,
        List<GradeAnswerView> answers,
        boolean manualGradingComplete
) {}
