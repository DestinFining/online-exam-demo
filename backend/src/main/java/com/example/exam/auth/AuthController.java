package com.example.exam.auth;

import com.example.exam.auth.dto.LoginRequest;
import com.example.exam.auth.dto.LoginResponse;
import com.example.exam.auth.dto.ProfileResponse;
import com.example.exam.auth.dto.RegisterRequest;
import com.example.exam.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success("注册成功", null);
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<ProfileResponse> me(Authentication authentication) {
        return ApiResponse.success(authService.profile(authentication.getName()));
    }

    /**
     * Invalidates the presented access token. The endpoint is authenticated, so
     * reaching it means the Authorization header carries the token to revoke.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authService.logout(authorization.substring(7));
        }
        return ApiResponse.success("已退出登录", null);
    }
}
