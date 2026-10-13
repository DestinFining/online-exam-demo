package com.example.exam.question;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("question")
public class Question {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long subjectId;
    private Long knowledgePointId;
    private String type;
    private String content;
    private String answerConfig;
    private String analysis;
    private String difficulty;
    private BigDecimal difficultyValue;
    private BigDecimal defaultScore;
    private String tags;
    private Integer status;
    private Long creatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }
    public Long getKnowledgePointId() { return knowledgePointId; }
    public void setKnowledgePointId(Long knowledgePointId) { this.knowledgePointId = knowledgePointId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getAnswerConfig() { return answerConfig; }
    public void setAnswerConfig(String answerConfig) { this.answerConfig = answerConfig; }
    public String getAnalysis() { return analysis; }
    public void setAnalysis(String analysis) { this.analysis = analysis; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public BigDecimal getDifficultyValue() { return difficultyValue; }
    public void setDifficultyValue(BigDecimal difficultyValue) { this.difficultyValue = difficultyValue; }
    public BigDecimal getDefaultScore() { return defaultScore; }
    public void setDefaultScore(BigDecimal defaultScore) { this.defaultScore = defaultScore; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
