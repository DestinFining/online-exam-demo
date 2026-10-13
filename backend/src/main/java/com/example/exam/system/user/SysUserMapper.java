package com.example.exam.system.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("""
        SELECT r.role_code
        FROM sys_role r
        JOIN sys_user_role ur ON ur.role_id = r.id
        WHERE ur.user_id = #{userId}
        ORDER BY r.id
        """)
    List<String> findRoleCodes(Long userId);
    @Select("""
        SELECT DISTINCT u.id
        FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id = u.id
        JOIN sys_role r ON r.id = ur.role_id
        WHERE r.role_code = #{roleCode}
        ORDER BY u.id
        """)
    List<Long> findUserIdsByRoleCode(@Param("roleCode") String roleCode);

}
