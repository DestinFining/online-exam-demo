package com.example.exam.auth;

import com.example.exam.auth.dto.LoginRequest;
import com.example.exam.auth.dto.LoginResponse;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.security.JwtService;
import com.example.exam.security.LoginAttemptService;
import com.example.exam.security.TokenRevocationService;
import com.example.exam.system.role.SysRoleMapper;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.system.user.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private SysUserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private LoginAttemptService loginAttemptService;
    private TokenRevocationService tokenRevocationService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(SysUserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        tokenRevocationService = mock(TokenRevocationService.class);
        authService = new AuthService(userMapper, mock(SysRoleMapper.class), mock(SysUserRoleMapper.class),
                passwordEncoder, jwtService, loginAttemptService, tokenRevocationService);
    }

    private static SysUser user(int status) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setNickname("管理员");
        user.setPassword("$2a$hashed");
        user.setStatus(status);
        return user;
    }

    @Test
    void rejectsALockedUsernameBeforeTouchingTheDatabase() {
        doThrow(new BusinessException(429, "登录失败次数过多"))
                .when(loginAttemptService).assertNotLocked("admin");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", "secret")));

        assertEquals(429, ex.getCode());
        verify(userMapper, never()).selectOne(any());
    }

    @Test
    void countsAWrongPasswordAsAFailureWithoutIssuingAToken() {
        when(userMapper.selectOne(any())).thenReturn(user(1));
        when(passwordEncoder.matches("wrong", "$2a$hashed")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", "wrong")));

        assertEquals(401, ex.getCode());
        verify(loginAttemptService).recordFailure("admin");
        verify(loginAttemptService, never()).clear(anyString());
        verify(jwtService, never()).generateToken(any(), anyString());
    }

    @Test
    void rejectsADisabledAccountWithoutCheckingThePassword() {
        when(userMapper.selectOne(any())).thenReturn(user(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.login(new LoginRequest("admin", "secret")));

        assertEquals(401, ex.getCode());
        verify(loginAttemptService).recordFailure("admin");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void clearsTheCounterAndReturnsATokenOnSuccess() {
        when(userMapper.selectOne(any())).thenReturn(user(1));
        when(passwordEncoder.matches("secret", "$2a$hashed")).thenReturn(true);
        when(userMapper.findRoleCodes(1L)).thenReturn(List.of("ADMIN"));
        when(jwtService.generateToken(1L, "admin")).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(7200L);

        LoginResponse response = authService.login(new LoginRequest(" admin ", "secret"));

        assertEquals("jwt-token", response.accessToken());
        assertEquals(List.of("ADMIN"), response.roles());
        assertEquals(7200L, response.expiresIn());
        verify(loginAttemptService).clear("admin");
        verify(userMapper).updateById(any(SysUser.class));
    }

    @Test
    void logoutRevokesThePresentedToken() {
        when(jwtService.isValid("jwt-token")).thenReturn(true);
        when(jwtService.extractTokenId("jwt-token")).thenReturn("jti-1");
        when(jwtService.getRemainingSeconds("jwt-token")).thenReturn(600L);

        authService.logout("jwt-token");

        verify(tokenRevocationService).revoke("jti-1", 600L);
    }

    @Test
    void logoutIgnoresMissingOrInvalidTokens() {
        when(jwtService.isValid("expired")).thenReturn(false);

        authService.logout("expired");
        authService.logout(null);
        authService.logout("   ");

        verifyNoInteractions(tokenRevocationService);
    }
}
