package com.example.exam.question;

import java.util.List;

/** Compact JSON representation of a question-bank page cached in Redis. */
public record QuestionPageCache(long current, long size, long total, List<QuestionListItem> records) {
}
