package com.example.exam.note;
import com.baomidou.mybatisplus.annotation.IdType;import com.baomidou.mybatisplus.annotation.TableId;import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
@TableName("question_note") public class QuestionNote{
 @TableId(type=IdType.AUTO) private Long id; private Long userId; private Long questionId; private String content; private LocalDateTime createdAt; private LocalDateTime updatedAt;
 public Long getId(){return id;}public void setId(Long v){id=v;} public Long getUserId(){return userId;}public void setUserId(Long v){userId=v;} public Long getQuestionId(){return questionId;}public void setQuestionId(Long v){questionId=v;} public String getContent(){return content;}public void setContent(String v){content=v;} public LocalDateTime getCreatedAt(){return createdAt;}public void setCreatedAt(LocalDateTime v){createdAt=v;} public LocalDateTime getUpdatedAt(){return updatedAt;}public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
