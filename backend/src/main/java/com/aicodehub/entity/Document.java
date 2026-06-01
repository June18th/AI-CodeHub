package com.aicodehub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String filename;
    private String fileType;
    private String visibility;  // PUBLIC / DEPARTMENT / PRIVATE
    private String orgTag;     // org tag this doc belongs to, e.g. "研发部" or "PRIVATE_alice"
    private String status;
    private Integer embeddingTokens; // token count from embedding API
    private String minioPath;        // MinIO object path for content retrieval
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
