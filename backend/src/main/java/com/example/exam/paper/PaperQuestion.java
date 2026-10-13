package com.example.exam.paper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("paper_question")
public class PaperQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paperId;
    private Long questionId;
    private Integer questionNo;
    private BigDecimal score;
    private Integer sort;
    private String contentSnapshot;
    private String typeSnapshot;
    private String answerConfigSnapshot;
    private String analysisSnapshot;
    private String difficultySnapshot;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long paperId) { this.paperId = paperId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Integer getQuestionNo() { return questionNo; }
    public void setQuestionNo(Integer questionNo) { this.questionNo = questionNo; }
    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public String getContentSnapshot() { return contentSnapshot; }
    public void setContentSnapshot(String contentSnapshot) { this.contentSnapshot = contentSnapshot; }
    public String getTypeSnapshot() { return typeSnapshot; }
    public void setTypeSnapshot(String typeSnapshot) { this.typeSnapshot = typeSnapshot; }
    public String getAnswerConfigSnapshot() { return answerConfigSnapshot; }
    public void setAnswerConfigSnapshot(String answerConfigSnapshot) { this.answerConfigSnapshot = answerConfigSnapshot; }
    public String getAnalysisSnapshot() { return analysisSnapshot; }
    public void setAnalysisSnapshot(String analysisSnapshot) { this.analysisSnapshot = analysisSnapshot; }
    public String getDifficultySnapshot() { return difficultySnapshot; }
    public void setDifficultySnapshot(String difficultySnapshot) { this.difficultySnapshot = difficultySnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
