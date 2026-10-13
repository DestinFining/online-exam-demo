package com.example.exam.favorite;
import java.time.LocalDateTime;
public record FavoriteView(Long id, Long questionId, String content, String type, String difficulty, LocalDateTime createdAt) {}
