package com.aicodehub.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("role_permission")
public class RolePermission {
    private String role;
    private Long permissionId;
}
