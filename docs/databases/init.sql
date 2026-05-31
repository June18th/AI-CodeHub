CREATE DATABASE IF NOT EXISTS aicodehub DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE aicodehub;

-- 对话会话
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    title       VARCHAR(256) NOT NULL DEFAULT '新对话' COMMENT '对话标题',
    model       VARCHAR(32)  NOT NULL DEFAULT 'deepseek' COMMENT '使用模型',
    slug        VARCHAR(8)            DEFAULT NULL COMMENT '8位短码',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_slug (slug),
    INDEX idx_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- 消息
CREATE TABLE IF NOT EXISTS message (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    conversation_id BIGINT       NOT NULL COMMENT '会话ID',
    role            VARCHAR(16)  NOT NULL COMMENT '角色: user/assistant',
    content         TEXT         NOT NULL COMMENT '消息内容',
    input_tokens    INT                   DEFAULT NULL COMMENT '输入Token数',
    output_tokens   INT                   DEFAULT NULL COMMENT '输出Token数',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_conv_created (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';

-- 用户
CREATE TABLE IF NOT EXISTS user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名',
    email         VARCHAR(128) NOT NULL COMMENT '邮箱',
    password       VARCHAR(256) NOT NULL COMMENT 'BCrypt 加密密码',
    avatar         VARCHAR(512)          DEFAULT NULL COMMENT '头像URL',
    refresh_token  VARCHAR(256)          DEFAULT NULL COMMENT 'BCrypt 加密刷新令牌',
    role          VARCHAR(16)  NOT NULL DEFAULT 'user' COMMENT '角色: admin/user/test',
    status        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '状态: pending/active/rejected',
    apply_reason  VARCHAR(512)          DEFAULT NULL COMMENT '申请理由',
    reviewed_by   BIGINT                DEFAULT NULL COMMENT '审核人ID',
    reviewed_at   DATETIME              DEFAULT NULL COMMENT '审核时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    INDEX idx_user_role_created (role, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 文档
CREATE TABLE IF NOT EXISTS document (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT       NOT NULL COMMENT '上传用户ID',
    filename    VARCHAR(256) NOT NULL COMMENT '文件名',
    file_type   VARCHAR(32)  NOT NULL DEFAULT 'txt' COMMENT '文件类型: txt/md/pdf',
    status      VARCHAR(16)  NOT NULL DEFAULT 'processing' COMMENT '状态: processing/ready/error',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_doc_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档表';

-- 文档分块
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

-- API 调用日志
CREATE TABLE IF NOT EXISTS audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT                DEFAULT NULL,
    username      VARCHAR(64)           DEFAULT NULL,
    model         VARCHAR(32)           DEFAULT NULL,
    endpoint      VARCHAR(256)          DEFAULT NULL,
    input_tokens  INT                   DEFAULT 0,
    output_tokens INT                   DEFAULT 0,
    latency_ms    INT                   DEFAULT 0,
    status        VARCHAR(16)           DEFAULT 'success',
    error_msg     VARCHAR(512)          DEFAULT NULL,
    created_at    DATETIME              DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_user_time (user_id, created_at),
    INDEX idx_created (created_at),
    INDEX idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API调用日志';

-- 模型配置
CREATE TABLE IF NOT EXISTS model_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    provider     VARCHAR(64)  NOT NULL COMMENT '供应商',
    config_name  VARCHAR(128) NOT NULL COMMENT '配置名称',
    api_url      VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'API地址',
    api_key      VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'API密钥',
    model        VARCHAR(128) NOT NULL DEFAULT '' COMMENT '模型名称',
    temperature  DOUBLE                DEFAULT 0.7 COMMENT '温度',
    capabilities VARCHAR(256)          DEFAULT '' COMMENT '能力标签',
    is_default   TINYINT               DEFAULT 0 COMMENT '是否默认',
    created_at   DATETIME              DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_mc_user_updated (user_id, updated_at),
    INDEX idx_mc_user_provider (user_id, provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';

-- 权限
CREATE TABLE IF NOT EXISTS permission (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(256) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表';

-- 角色权限映射
CREATE TABLE IF NOT EXISTS role_permission (
    role          VARCHAR(16) NOT NULL,
    permission_id BIGINT      NOT NULL,
    PRIMARY KEY (role, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限映射';

-- 工作流定义
CREATE TABLE IF NOT EXISTS workflow (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(256) NOT NULL,
    description VARCHAR(512) DEFAULT '',
    definition  JSON         NOT NULL,
    status      VARCHAR(16)  DEFAULT 'draft',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_wf_user_updated (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流定义表';

-- 工作流执行记录
CREATE TABLE IF NOT EXISTS workflow_run (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_id   BIGINT       NOT NULL,
    user_id       BIGINT       NOT NULL,
    input_params  JSON                  DEFAULT NULL,
    status        VARCHAR(16)           DEFAULT 'running',
    current_step  VARCHAR(64)           DEFAULT NULL,
    step_results  JSON                  DEFAULT NULL,
    final_output  TEXT,
    error_msg     VARCHAR(1024)         DEFAULT NULL,
    started_at    DATETIME              DEFAULT CURRENT_TIMESTAMP,
    finished_at   DATETIME              DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_wfr_workflow (workflow_id),
    INDEX idx_wfr_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流执行记录';

-- Agent 异步任务
CREATE TABLE IF NOT EXISTS agent_task (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    prompt      TEXT         NOT NULL,
    model_type  VARCHAR(32)  DEFAULT 'deepseek',
    status      VARCHAR(16)  DEFAULT 'pending' COMMENT 'pending/running/done/failed',
    result      MEDIUMTEXT,
    error_msg   VARCHAR(1024),
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_task_user (user_id),
    INDEX idx_task_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent异步任务';
