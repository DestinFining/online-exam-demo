package com.example.exam.statistics;

/** One knowledge-point aggregation row returned by a SQL JOIN/GROUP BY query. */
public class KnowledgePointPracticeStats {
    private Long knowledgePointId;
    private String knowledgePointName;
    private long attempts;
    private long correct;

    public Long getKnowledgePointId() { return knowledgePointId; }
    public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
    public String getKnowledgePointName() { return knowledgePointName; }
    public void setKnowledgePointName(String knowledgePointName) { this.knowledgePointName = knowledgePointName; }
    public long getAttempts() { return attempts; }
    public void setAttempts(long attempts) { this.attempts = attempts; }
    public long getCorrect() { return correct; }
    public void setCorrect(long correct) { this.correct = correct; }
}
