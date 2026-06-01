package com.aicodehub.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("file_upload")
public class FileUpload {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileMd5;        // MD5 of file content
    private String filename;
    private Long fileSize;         // total bytes
    private Integer totalChunks;
    private String fileType;
    private Long userId;
    private String visibility;     // PUBLIC/DEPARTMENT/PRIVATE
    private String orgTag;
    private String status;         // uploading/merging/completed/failed
    private Long documentId;       // linked document after processing
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;
}
