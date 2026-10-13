package com.example.exam.system.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.exam.common.exception.BusinessException;
import com.example.exam.system.role.SysRole;
import com.example.exam.system.role.SysRoleMapper;
import com.example.exam.system.role.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class UserAdminService {
    private static final Map<String, String> ROLE_NAMES = Map.of(
            "STUDENT", "学生", "TEACHER", "教师", "ADMIN", "管理员");
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper,
                            SysRoleMapper roleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper; this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper; this.passwordEncoder = passwordEncoder;
    }

    public Page<UserAdminResponse> page(int page, int size, String keyword, String roleCode, Integer status) {
        int current = Math.max(page, 1), pageSize = Math.min(Math.max(size, 1), 100);
        QueryWrapper<SysUser> query = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(w -> w.like("username", value).or().like("nickname", value).or().like("email", value).or().like("phone", value));
        }
        if (status != null) query.eq("status", status);
        if (roleCode != null && !roleCode.isBlank()) {
            String normalized = normalizeRole(roleCode);
            SysRole role = roleMapper.findByCode(normalized);
            if (role == null) throw new BusinessException(400, "角色不存在：" + normalized);
            List<Long> userIds = userRoleMapper.selectList(new QueryWrapper<UserRole>().eq("role_id", role.getId()))
                    .stream().map(UserRole::getUserId).distinct().toList();
            if (userIds.isEmpty()) {
                Page<UserAdminResponse> empty = new Page<>(current, pageSize, 0);
                empty.setRecords(List.of()); return empty;
            }
            query.in("id", userIds);
        }
        query.orderByDesc("id");
        Page<SysUser> source = userMapper.selectPage(new Page<>(current, pageSize), query);
        Page<UserAdminResponse> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(source.getRecords().stream().map(this::toResponse).toList());
        return target;
    }

    public UserAdminResponse get(Long id) { return toResponse(requireUser(id)); }

    public List<Map<String, String>> roleOptions() {
        return List.of(
                Map.of("code", "STUDENT", "name", "学生"),
                Map.of("code", "TEACHER", "name", "教师"),
                Map.of("code", "ADMIN", "name", "管理员"));
    }

    @Transactional
    public UserAdminResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        if (userMapper.selectCount(new QueryWrapper<SysUser>().eq("username", username)) > 0)
            throw new BusinessException(409, "用户名已存在");
        String email = blankToNull(request.email());
        if (email != null && userMapper.selectCount(new QueryWrapper<SysUser>().eq("email", email)) > 0)
            throw new BusinessException(409, "邮箱已被使用");
        String phone = blankToNull(request.phone());
        if (phone != null && userMapper.selectCount(new QueryWrapper<SysUser>().eq("phone", phone)) > 0)
            throw new BusinessException(409, "手机号已被使用");
        SysUser user = new SysUser();
        user.setUsername(username); user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname().trim()); user.setEmail(email); user.setPhone(phone);
        user.setStatus(request.status() == null ? 1 : normalizeStatus(request.status()));
        userMapper.insert(user);
        replaceRoles(user.getId(), request.roleCodes());
        return toResponse(user);
    }

    @Transactional
    public UserAdminResponse update(Long id, UpdateUserRequest request, String operatorUsername) {
        SysUser user = requireUser(id);
        SysUser operator = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", operatorUsername).last("LIMIT 1"));
        if (operator != null && operator.getId().equals(id)) {
            if (Integer.valueOf(0).equals(request.status())) throw new BusinessException(409, "不能停用当前登录账号");
            if (request.roleCodes() != null && request.roleCodes().stream().noneMatch(code -> "ADMIN".equalsIgnoreCase(code == null ? "" : code.trim())))
                throw new BusinessException(409, "不能移除当前登录账号的管理员角色");
        }
        String nickname = blankToNull(request.nickname());
        if (nickname != null) user.setNickname(nickname);
        String email = blankToNull(request.email());
        if (email != null && userMapper.selectCount(new QueryWrapper<SysUser>().eq("email", email).ne("id", id)) > 0)
            throw new BusinessException(409, "邮箱已被其他用户使用");
        user.setEmail(email);
        String phone = blankToNull(request.phone());
        if (phone != null && userMapper.selectCount(new QueryWrapper<SysUser>().eq("phone", phone).ne("id", id)) > 0)
            throw new BusinessException(409, "手机号已被其他用户使用");
        user.setPhone(phone);
        if (request.password() != null && !request.password().isBlank()) {
            if (request.password().length() < 8 || request.password().length() > 72) throw new BusinessException(400, "新密码长度须为 8-72 个字符");
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        if (request.status() != null) user.setStatus(normalizeStatus(request.status()));
        userMapper.updateById(user);
        if (request.roleCodes() != null) replaceRoles(id, request.roleCodes());
        return toResponse(user);
    }

    @Transactional
    public void disable(Long id, String operatorUsername) {
        SysUser user = requireUser(id);
        SysUser operator = userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", operatorUsername).last("LIMIT 1"));
        if (operator != null && operator.getId().equals(id)) throw new BusinessException(409, "不能停用当前登录账号");
        if (Integer.valueOf(0).equals(user.getStatus())) return;
        user.setStatus(0); userMapper.updateById(user);
    }

    private void replaceRoles(Long userId, List<String> codes) {
        if (codes == null || codes.isEmpty()) throw new BusinessException(400, "请至少分配一个角色");
        Set<String> normalized = new LinkedHashSet<>();
        for (String code : codes) {
            String value = normalizeRole(code);
            if (!ROLE_NAMES.containsKey(value)) throw new BusinessException(400, "不支持的角色：" + value);
            normalized.add(value);
        }
        List<SysRole> roles = new ArrayList<>();
        for (String code : normalized) {
            SysRole role = roleMapper.findByCode(code);
            if (role == null) throw new BusinessException(500, "系统缺少角色初始化数据：" + code);
            roles.add(role);
        }
        userRoleMapper.delete(new QueryWrapper<UserRole>().eq("user_id", userId));
        for (SysRole role : roles) {
            UserRole relation = new UserRole(); relation.setUserId(userId); relation.setRoleId(role.getId());
            userRoleMapper.insert(relation);
        }
    }

    private UserAdminResponse toResponse(SysUser user) {
        return new UserAdminResponse(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(),
                user.getPhone(), user.getStatus(), user.getLastLoginAt(), user.getCreatedAt(), userMapper.findRoleCodes(user.getId()));
    }
    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(404, "用户不存在");
        return user;
    }
    private int normalizeStatus(int status) {
        if (status != 0 && status != 1) throw new BusinessException(400, "用户状态只能为启用或停用");
        return status;
    }
    private String normalizeRole(String role) { return role == null ? "" : role.trim().toUpperCase(Locale.ROOT); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
