package com.aicodehub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("workflow_run")
public class WorkflowRun {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long workflowId;
    private Long userId;
    private String inputParams;   // JSON
    private String status;        // running / success / failed
    private String currentStep;
    private String stepResults;   // JSON
    private String finalOutput;
    private String errorMsg;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
