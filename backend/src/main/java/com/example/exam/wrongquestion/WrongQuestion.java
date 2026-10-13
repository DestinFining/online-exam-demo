package com.example.exam.wrongquestion;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("wrong_question")
public class WrongQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long questionId;
    private Integer wrongCount;
    private Integer redoCount;
    private Integer masteryLevel;
    private LocalDateTime lastWrongAt;
    private LocalDateTime lastCorrectAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
    public Integer getRedoCount() { return redoCount; }
    public void setRedoCount(Integer redoCount) { this.redoCount = redoCount; }
    public Integer getMasteryLevel() { return masteryLevel; }
    public void setMasteryLevel(Integer masteryLevel) { this.masteryLevel = masteryLevel; }
    public LocalDateTime getLastWrongAt() { return lastWrongAt; }
    public void setLastWrongAt(LocalDateTime lastWrongAt) { this.lastWrongAt = lastWrongAt; }
    public LocalDateTime getLastCorrectAt() { return lastCorrectAt; }
    public void setLastCorrectAt(LocalDateTime lastCorrectAt) { this.lastCorrectAt = lastCorrectAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
