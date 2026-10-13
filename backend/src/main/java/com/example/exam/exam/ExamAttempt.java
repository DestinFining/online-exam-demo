package com.example.exam.exam;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("exam_attempt")
public class ExamAttempt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long examId;
    private Long userId;
    private Integer attemptNo;
    private LocalDateTime startTime;
    private LocalDateTime submitTime;
    private String status;
    private BigDecimal objectiveScore;
    private BigDecimal subjectiveScore;
    private BigDecimal totalScore;
    private Integer cheatCount;
    private LocalDateTime lastSavedAt;
    private Long versionNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getExamId() { return examId; }
    public void setExamId(Long examId) { this.examId = examId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer attemptNo) { this.attemptNo = attemptNo; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getSubmitTime() { return submitTime; }
    public void setSubmitTime(LocalDateTime submitTime) { this.submitTime = submitTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getObjectiveScore() { return objectiveScore; }
    public void setObjectiveScore(BigDecimal objectiveScore) { this.objectiveScore = objectiveScore; }
    public BigDecimal getSubjectiveScore() { return subjectiveScore; }
    public void setSubjectiveScore(BigDecimal subjectiveScore) { this.subjectiveScore = subjectiveScore; }
    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }
    public Integer getCheatCount() { return cheatCount; }
    public void setCheatCount(Integer cheatCount) { this.cheatCount = cheatCount; }
    public LocalDateTime getLastSavedAt() { return lastSavedAt; }
    public void setLastSavedAt(LocalDateTime lastSavedAt) { this.lastSavedAt = lastSavedAt; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
