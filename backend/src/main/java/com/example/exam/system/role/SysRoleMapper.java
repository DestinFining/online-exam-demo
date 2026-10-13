package com.example.exam.system.role;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

public interface SysRoleMapper extends BaseMapper<SysRole> {
    @Select("SELECT id, role_code, role_name, description, created_at, updated_at FROM sys_role WHERE role_code = #{roleCode} LIMIT 1")
    SysRole findByCode(String roleCode);
}
