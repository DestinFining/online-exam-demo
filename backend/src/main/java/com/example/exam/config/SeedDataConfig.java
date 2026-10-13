package com.example.exam.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.exam.system.role.SysRole;
import com.example.exam.system.role.SysRoleMapper;
import com.example.exam.system.role.UserRole;
import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.system.user.SysUserRoleMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Creates the demo accounts (admin / teacher / student) with well-known passwords.
 * It is restricted to the {@code dev} profile so that a production deployment can
 * never come up with reachable default credentials. The {@code local} profile
 * group includes {@code dev} (see application.yml), so local development is
 * unaffected.
 */
@Configuration
@Profile("dev")
public class SeedDataConfig {
    @Bean
    CommandLineRunner seedUsers(SysUserMapper userMapper,
                                SysRoleMapper roleMapper,
                                SysUserRoleMapper userRoleMapper,
                                PasswordEncoder passwordEncoder) {
        return new OrderedCommandLineRunner(1, args -> {
            createUserIfMissing(userMapper, roleMapper, userRoleMapper, passwordEncoder,
                    "admin", "admin123456", "系统管理员", "ADMIN");
            createUserIfMissing(userMapper, roleMapper, userRoleMapper, passwordEncoder,
                    "teacher", "teacher123456", "演示教师", "TEACHER");
            createUserIfMissing(userMapper, roleMapper, userRoleMapper, passwordEncoder,
                    "student", "student123456", "演示学生", "STUDENT");
        });
    }

    private void createUserIfMissing(SysUserMapper userMapper,
                                     SysRoleMapper roleMapper,
                                     SysUserRoleMapper userRoleMapper,
                                     PasswordEncoder encoder,
                                     String username,
                                     String password,
                                     String nickname,
                                     String roleCode) {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>()
                .eq("username", username).last("LIMIT 1"));
        if (user != null) return;

        SysRole role = roleMapper.findByCode(roleCode);
        if (role == null) throw new IllegalStateException("缺少角色: " + roleCode);

        user = new SysUser();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setNickname(nickname);
        user.setStatus(1);
        userMapper.insert(user);

        UserRole relation = new UserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(role.getId());
        userRoleMapper.insert(relation);
    }
}
