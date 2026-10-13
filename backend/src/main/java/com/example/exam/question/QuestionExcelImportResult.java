package com.example.exam.question;

import java.util.List;

public record QuestionExcelImportResult(int totalRows, int importedCount, int failedCount, List<String> errors) {}
