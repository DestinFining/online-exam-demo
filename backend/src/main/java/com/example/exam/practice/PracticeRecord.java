package com.example.exam.practice;
import com.baomidou.mybatisplus.annotation.IdType;import com.baomidou.mybatisplus.annotation.TableId;import com.baomidou.mybatisplus.annotation.TableName;import java.time.LocalDateTime;
@TableName("practice_record") public class PracticeRecord{
 @TableId(type=IdType.AUTO) private Long id;private Long userId;private Long questionId;private String sessionType;private String answerContent;private Integer isCorrect;private LocalDateTime createdAt;
 public Long getId(){return id;}public void setId(Long v){id=v;} public Long getUserId(){return userId;}public void setUserId(Long v){userId=v;} public Long getQuestionId(){return questionId;}public void setQuestionId(Long v){questionId=v;} public String getSessionType(){return sessionType;}public void setSessionType(String v){sessionType=v;} public String getAnswerContent(){return answerContent;}public void setAnswerContent(String v){answerContent=v;} public Integer getIsCorrect(){return isCorrect;}public void setIsCorrect(Integer v){isCorrect=v;} public LocalDateTime getCreatedAt(){return createdAt;}public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
