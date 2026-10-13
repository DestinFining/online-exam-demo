package com.example.exam.practice;
import com.example.exam.common.response.ApiResponse;import jakarta.validation.Valid;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/practice") public class PracticeController{
 private final PracticeService service;public PracticeController(PracticeService service){this.service=service;}
 @GetMapping("/count") @PreAuthorize("hasAuthority('practice:start')") public ApiResponse<?> count(
         @RequestParam(required=false) Long subjectId,
         @RequestParam(required=false) Long knowledgePointId,
         @RequestParam(required=false) String type,
         @RequestParam(required=false) String difficulty){
   return ApiResponse.success(service.countAvailable(subjectId, knowledgePointId, type, difficulty));
 }
 @PostMapping("/start") @PreAuthorize("hasAuthority('practice:start')") public ApiResponse<?> start(@Valid @RequestBody PracticeStartRequest req){return ApiResponse.success(service.start(req));}
 @PostMapping("/answer") @PreAuthorize("hasAuthority('practice:start')") public ApiResponse<?> answer(@Valid @RequestBody PracticeAnswerRequest req,Authentication a){return ApiResponse.success(service.answer(req,a.getName()));}
}
