package com.example.exam.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.auth.dto.LoginRequest;
import com.example.exam.auth.dto.LoginResponse;
import com.example.exam.auth.dto.ProfileResponse;
import com.example.exam.auth.dto.RegisterRequest;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.security.JwtService;
import com.example.exam.security.LoginAttemptService;
import com.example.exam.security.TokenRevocationService;
import com.example.exam.system.role.SysRole;
import com.example.exam.system.role.SysRoleMapper;
import com.example.exam.system.role.UserRole;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.system.user.SysUserRoleMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthService {
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final TokenRevocationService tokenRevocationService;

    public AuthService(SysUserMapper userMapper,
                       SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       LoginAttemptService loginAttemptService,
                       TokenRevocationService tokenRevocationService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.tokenRevocationService = tokenRevocationService;
    }

    @Transactional
    public void register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new BusinessException(400, "两次密码不一致");
        }
        if (existsByUsername(request.username())) {
            throw new BusinessException(409, "用户名已存在");
        }
        if (request.email() != null && !request.email().isBlank() && existsByEmail(request.email())) {
            throw new BusinessException(409, "邮箱已存在");
        }
        if (request.phone() != null && !request.phone().isBlank() && existsByPhone(request.phone())) {
            throw new BusinessException(409, "手机号已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.username().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim());
        user.setEmail(blankToNull(request.email()));
        user.setPhone(blankToNull(request.phone()));
        user.setStatus(1);
        userMapper.insert(user);

        SysRole studentRole = roleMapper.findByCode("STUDENT");
        if (studentRole == null) {
            throw new BusinessException(500, "系统缺少STUDENT角色初始化数据");
        }
        UserRole relation = new UserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(studentRole.getId());
        userRoleMapper.insert(relation);
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        // Throttling happens before the password lookup so a locked account cannot
        // be used to keep probing credentials (and the 429 is distinguishable from
        // the generic 401 only for the account owner, who already knows the name).
        loginAttemptService.assertNotLocked(username);
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>()
                .eq("username", username)
                .last("LIMIT 1"));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            loginAttemptService.recordFailure(username);
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            loginAttemptService.recordFailure(username);
            throw new BusinessException(401, "用户名或密码错误");
        }
        loginAttemptService.clear(username);

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        List<String> roles = userMapper.findRoleCodes(user.getId());
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                roles,
                token,
                jwtService.getExpirationSeconds()
        );
    }

    /**
     * Revokes a single access token so the client can log out before its TTL runs
     * out. Invalid or already-expired tokens are ignored: the outcome the caller
     * wants is already true.
     */
    public void logout(String token) {
        if (token == null || token.isBlank() || !jwtService.isValid(token)) {
            return;
        }
        tokenRevocationService.revoke(jwtService.extractTokenId(token), jwtService.getRemainingSeconds(token));
    }

    public ProfileResponse profile(String username) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>()
                .eq("username", username)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return new ProfileResponse(
                user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getPhone(), userMapper.findRoleCodes(user.getId())
        );
    }

    private boolean existsByUsername(String username) {
        return userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", username.trim())) > 0;
    }

    private boolean existsByEmail(String email) {
        return userMapper.selectCount(new QueryWrapper<SysUser>().eq("email", email.trim())) > 0;
    }

    private boolean existsByPhone(String phone) {
        return userMapper.selectCount(new QueryWrapper<SysUser>().eq("phone", phone.trim())) > 0;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
