package com.example.exam.practice;
import jakarta.validation.constraints.NotNull;
public record PracticeAnswerRequest(@NotNull Long questionId,Object answerContent,String mode){}
