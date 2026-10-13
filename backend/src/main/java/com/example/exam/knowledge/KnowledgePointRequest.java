package com.example.exam.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KnowledgePointRequest(
        @NotNull(message = "学科不能为空") Long subjectId,
        Long parentId,
        @NotBlank(message = "知识点名称不能为空")
        @Size(max = 100, message = "知识点名称长度不能超过100")
        String name,
        @Size(max = 500, message = "描述长度不能超过500")
        String description,
        Integer sort,
        Integer status
) {}
