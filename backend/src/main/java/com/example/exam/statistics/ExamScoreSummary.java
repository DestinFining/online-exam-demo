package com.example.exam.statistics;

import java.math.BigDecimal;

/** Aggregate score overview for a student's published exam attempts. */
public class ExamScoreSummary {
    private long examCount;
    private BigDecimal averageScore;
    private BigDecimal highestScore;

    public long getExamCount() { return examCount; }
    public void setExamCount(long examCount) { this.examCount = examCount; }
    public BigDecimal getAverageScore() { return averageScore; }
    public void setAverageScore(BigDecimal averageScore) { this.averageScore = averageScore; }
    public BigDecimal getHighestScore() { return highestScore; }
    public void setHighestScore(BigDecimal highestScore) { this.highestScore = highestScore; }
}
