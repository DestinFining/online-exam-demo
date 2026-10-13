package com.example.exam.note;
import com.example.exam.common.response.ApiResponse;import jakarta.validation.Valid;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/question-notes") public class QuestionNoteController{
 private final QuestionNoteService service;public QuestionNoteController(QuestionNoteService service){this.service=service;}
 @GetMapping("/question/{questionId}") @PreAuthorize("hasAuthority('note:manage')") public ApiResponse<?> list(@PathVariable Long questionId,Authentication a){return ApiResponse.success(service.list(questionId,a.getName()));}
 @PostMapping("/question/{questionId}") @PreAuthorize("hasAuthority('note:manage')") public ApiResponse<?> create(@PathVariable Long questionId,@Valid @RequestBody NoteRequest req,Authentication a){return ApiResponse.success("笔记已保存",service.create(questionId,req,a.getName()));}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('note:manage')") public ApiResponse<?> update(@PathVariable Long id,@Valid @RequestBody NoteRequest req,Authentication a){return ApiResponse.success("笔记已更新",service.update(id,req,a.getName()));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('note:manage')") public ApiResponse<?> delete(@PathVariable Long id,Authentication a){service.delete(id,a.getName());return ApiResponse.success("笔记已删除",null);}
}
