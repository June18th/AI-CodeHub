package com.aicodehub.mapper;

import com.aicodehub.entity.Permission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {

    @Select("SELECT p.code FROM permission p INNER JOIN role_permission rp ON p.id = rp.permission_id WHERE rp.role = #{role}")
    List<String> findCodesByRole(String role);
}
