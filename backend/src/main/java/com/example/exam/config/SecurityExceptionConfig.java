package com.example.exam.config;

import com.example.exam.common.response.ApiResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
public class SecurityExceptionConfig {
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> writeJson(objectMapper, response, 401, "未登录或Token无效");
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeJson(objectMapper, response, 403, "无权限访问");
    }

    private void writeJson(ObjectMapper mapper, HttpServletResponse response, int code, String message)
            throws java.io.IOException {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
    }
}
