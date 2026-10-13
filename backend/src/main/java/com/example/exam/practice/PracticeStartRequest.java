package com.example.exam.practice;
import jakarta.validation.constraints.Max;import jakarta.validation.constraints.Min;
public record PracticeStartRequest(Long subjectId,Long knowledgePointId,String type,String difficulty,@Min(1) @Max(5000) Integer count,String mode){public int safeCount(){return count==null?10:count;}}
