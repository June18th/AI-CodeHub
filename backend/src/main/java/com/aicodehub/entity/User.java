package com.aicodehub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private String password;
    private String avatar;
    private String refreshToken;
    private String orgTags;     // comma-separated tag names: "DEFAULT,PRIVATE_alice,研发部"
    private String primaryOrg;  // primary org tag name: "PRIVATE_alice"
    private String role;    // admin / user / test / applicant
    private String status;  // pending / active / rejected
    private String applyReason;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
