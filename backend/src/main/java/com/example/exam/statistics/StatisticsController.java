package com.example.exam.statistics;

import com.example.exam.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {
    private final StatisticsService service;
    private final TeacherStatisticsService teacherService;
    public StatisticsController(StatisticsService service, TeacherStatisticsService teacherService) {
        this.service = service; this.teacherService = teacherService;
    }
    @GetMapping("/student")
    @PreAuthorize("hasAuthority('statistics:view:self')")
    public ApiResponse<StudentStatistics> student(Authentication authentication) {
        return ApiResponse.success(service.student(authentication.getName()));
    }
    @GetMapping("/teacher")
    @PreAuthorize("hasAuthority('statistics:view:exam')")
    public ApiResponse<TeacherStatistics> teacher(Authentication authentication, @RequestParam(required = false) Long teacherId) {
        boolean admin = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("ROLE_ADMIN"::equals);
        return ApiResponse.success(teacherService.overview(authentication.getName(), teacherId, admin));
    }
}
