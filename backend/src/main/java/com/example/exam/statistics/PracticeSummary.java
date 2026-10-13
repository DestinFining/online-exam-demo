package com.example.exam.statistics;

/** Aggregated practice totals for one student, computed in the database. */
public class PracticeSummary {
    private long totalPractice;
    private long correctPractice;
    private long wrongPractice;

    public long getTotalPractice() { return totalPractice; }
    public void setTotalPractice(long totalPractice) { this.totalPractice = totalPractice; }
    public long getCorrectPractice() { return correctPractice; }
    public void setCorrectPractice(long correctPractice) { this.correctPractice = correctPractice; }
    public long getWrongPractice() { return wrongPractice; }
    public void setWrongPractice(long wrongPractice) { this.wrongPractice = wrongPractice; }
}
