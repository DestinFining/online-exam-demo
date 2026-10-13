package com.example.exam.practice;
import java.math.BigDecimal;import java.util.List;
public record PracticeQuestionView(Long id,String type,String content,String difficulty,BigDecimal defaultScore,List<Option> options){public record Option(Long id,String key,String content,Integer sort) {}}
