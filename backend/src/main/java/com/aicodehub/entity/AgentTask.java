package com.aicodehub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("agent_task")
public class AgentTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String prompt;
    private String modelType;
    private String status;  // pending / running / done / failed
    private String result;
    private String errorMsg;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
