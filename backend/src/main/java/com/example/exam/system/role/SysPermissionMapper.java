package com.example.exam.system.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysPermissionMapper extends BaseMapper<SysPermission> {
    @Select("""
        SELECT DISTINCT p.permission_code
        FROM sys_permission p
        JOIN sys_role_permission rp ON rp.permission_id = p.id
        JOIN sys_user_role ur ON ur.role_id = rp.role_id
        WHERE ur.user_id = #{userId}
        ORDER BY p.permission_code
        """)
    List<String> findPermissionCodes(Long userId);
}
