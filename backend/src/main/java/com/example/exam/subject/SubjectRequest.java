package com.example.exam.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubjectRequest(
        @NotBlank(message = "学科名称不能为空")
        @Size(max = 100, message = "学科名称长度不能超过100")
        String name,
        @Size(max = 500, message = "描述长度不能超过500")
        String description,
        Integer status,
        Integer sort
) {}
