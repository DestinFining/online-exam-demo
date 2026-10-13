package com.example.exam.question;

import com.example.exam.common.response.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/questions/excel")
public class QuestionExcelController {
    private final QuestionExcelService service;
    public QuestionExcelController(QuestionExcelService service) { this.service = service; }

    @GetMapping("/template")
    @PreAuthorize("hasAuthority('question:import')")
    public ResponseEntity<byte[]> template() { return attachment(service.template(), "题目导入模板.xlsx"); }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('question:export')")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long subjectId,
                                         @RequestParam(required = false) Long knowledgePointId,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String difficulty,
                                         @RequestParam(required = false) String keyword) {
        return attachment(service.exportExcel(subjectId, knowledgePointId, type, difficulty, keyword), "题库导出.xlsx");
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('question:import')")
    public ApiResponse<QuestionExcelImportResult> importExcel(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return ApiResponse.success("Excel 导入已处理", service.importExcel(file, authentication.getName()));
    }

    private ResponseEntity<byte[]> attachment(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
