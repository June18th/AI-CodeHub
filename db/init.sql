CREATE DATABASE IF NOT EXISTS aicodehub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE aicodehub;

CREATE TABLE IF NOT EXISTS conversation (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    title       VARCHAR(256) NOT NULL DEFAULT '新对话' COMMENT '对话标题',
    model       VARCHAR(32)  NOT NULL DEFAULT 'deepseek' COMMENT '使用模型',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

CREATE TABLE IF NOT EXISTS message (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id BIGINT       NOT NULL COMMENT '会话ID',
    role            VARCHAR(16)  NOT NULL COMMENT '角色: user/assistant',
    content         TEXT         NOT NULL COMMENT '消息内容',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

CREATE TABLE IF NOT EXISTS document (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT       NOT NULL COMMENT '上传用户ID',
    filename    VARCHAR(256) NOT NULL COMMENT '文件名',
    file_type   VARCHAR(32)  NOT NULL DEFAULT 'txt' COMMENT '文件类型: txt/md/pdf',
    status      VARCHAR(16)  NOT NULL DEFAULT 'processing' COMMENT '状态: processing/ready/error',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_doc_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';

CREATE TABLE IF NOT EXISTS document_chunk (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    document_id BIGINT       NOT NULL COMMENT '文档ID',
    chunk_index INT          NOT NULL COMMENT '分块序号',
    content     TEXT         NOT NULL COMMENT '分块内容',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_chunk_doc (document_id),
    FULLTEXT INDEX ft_content (content)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档分块表';

CREATE TABLE IF NOT EXISTS user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名',
    email         VARCHAR(128) NOT NULL COMMENT '邮箱',
    password      VARCHAR(256) NOT NULL COMMENT 'BCrypt 加密密码',
    avatar        VARCHAR(512)          DEFAULT NULL COMMENT '头像URL',
    role          VARCHAR(16)  NOT NULL DEFAULT 'applicant' COMMENT '角色: admin/beta/user/applicant',
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '状态: pending/active/rejected',
    apply_reason  VARCHAR(512)          DEFAULT NULL COMMENT '申请理由',
    reviewed_by   BIGINT                DEFAULT NULL COMMENT '审核人ID',
    reviewed_at   DATETIME              DEFAULT NULL COMMENT '审核时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
