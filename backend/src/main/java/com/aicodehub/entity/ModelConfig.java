package com.aicodehub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("model_config")
public class ModelConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String provider;
    private String configName;
    private String apiUrl;
    private String apiKey;
    private String model;
    private Double temperature;
    private String capabilities;
    private Integer isDefault;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
