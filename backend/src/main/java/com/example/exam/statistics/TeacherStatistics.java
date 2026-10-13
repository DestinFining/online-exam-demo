package com.example.exam.statistics;

import java.math.BigDecimal;
import java.util.List;

public record TeacherStatistics(
        String teacherScope,
        long examCount,
        long paperCount,
        long publishedAttemptCount,
        BigDecimal averageScoreRate,
        List<ScoreBand> scoreDistribution,
        List<PaperDifficulty> paperDifficulties
) {
    public record ScoreBand(String label, long count, BigDecimal percentage) {}
    public record PaperDifficulty(
            Long paperId, String paperName, int questionCount, BigDecimal totalScore,
            BigDecimal difficultyIndex, String difficultyLabel, BigDecimal averageScoreRate, String basis
    ) {}
}
