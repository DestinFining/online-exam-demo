package com.example.exam.security;

import com.example.exam.system.user.SysUser;
import com.example.exam.system.user.SysUserMapper;
import com.example.exam.system.role.SysPermissionMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final SysUserMapper userMapper;
    private final SysPermissionMapper permissionMapper;

    public CustomUserDetailsService(SysUserMapper userMapper, SysPermissionMapper permissionMapper) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SysUser>()
                        .eq("username", username)
                        .last("LIMIT 1")
        );
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        var roles = userMapper.findRoleCodes(user.getId());
        var authorities = new java.util.ArrayList<SimpleGrantedAuthority>();
        roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).forEach(authorities::add);
        permissionMapper.findPermissionCodes(user.getId()).stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.getStatus() != null && user.getStatus() == 1,
                true,
                true,
                true,
                authorities
        );
    }
}
