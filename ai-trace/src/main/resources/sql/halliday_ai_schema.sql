-- -----------------------------------------------------
-- Schema initialization script for halliday_ai trace persistence
-- MySQL 8.0 compatible
-- -----------------------------------------------------
CREATE DATABASE IF NOT EXISTS halliday_ai
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE halliday_ai;

-- 1. 会话主表：记录一次完整的对话会话
CREATE TABLE IF NOT EXISTS ai_trace_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    trace_id VARCHAR(64) NOT NULL COMMENT '全链路追踪ID（WS 建立时生成，全局唯一）',
    user_id BIGINT NULL COMMENT '用户 ID，可为空',
    round_index INT NOT NULL DEFAULT 0 COMMENT '当前会话最大轮次（从 0 开始）',
    start_time DATETIME NOT NULL COMMENT '会话开始时间',
    end_time DATETIME NULL COMMENT '会话结束时间',
    duration_ms BIGINT NULL COMMENT '会话总耗时（毫秒），由业务代码计算写入',
    llm_model VARCHAR(128) NULL COMMENT '所用大模型名称',
    llm_input_tokens INT NULL COMMENT '大模型输入 Token 数',
    llm_output_tokens INT NULL COMMENT '大模型输出 Token 数',
    llm_total_tokens INT NULL COMMENT '大模型 Token 总数，由代码写入',
    status VARCHAR(32) NOT NULL DEFAULT 'running' COMMENT '会话状态：running/success/failed',
    error_message TEXT NULL COMMENT '会话级错误摘要',
    remark VARCHAR(255) NULL COMMENT '备注信息',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    UNIQUE KEY uk_trace (trace_id),
    KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 全链路主表：记录每次完整会话的核心信息';

-- 2. 事件追踪表：保存编排过程中的关键事件
CREATE TABLE IF NOT EXISTS ai_trace_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    trace_id VARCHAR(64) NOT NULL COMMENT '关联的会话追踪 ID',
    user_id BIGINT NULL COMMENT '用户 ID，可为空',
    round_index INT NOT NULL DEFAULT 0 COMMENT '轮次编号，从 0 开始',
    phase VARCHAR(32) NOT NULL COMMENT '阶段：orchestrator/stt/llm/tts',
    event_name VARCHAR(64) NOT NULL COMMENT '事件名称：start/finish/timeout/retry/error 等',
    message TEXT NULL COMMENT '事件说明文字',
    metadata JSON NULL COMMENT '事件附加元数据（JSON 格式）',
    timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间戳',
    duration_ms BIGINT NULL COMMENT '事件耗时（毫秒），由业务代码计算写入',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_trace_round (trace_id, round_index),
    KEY idx_phase_time (phase, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件追踪表：记录各阶段关键事件与时序信息';

-- 3. 错误记录表：保存异常链路
CREATE TABLE IF NOT EXISTS ai_trace_error (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    trace_id VARCHAR(64) NOT NULL COMMENT '关联的会话追踪 ID',
    user_id BIGINT NULL COMMENT '用户 ID，可为空',
    round_index INT NOT NULL DEFAULT 0 COMMENT '轮次编号，从 0 开始',
    phase VARCHAR(32) NOT NULL COMMENT '出错阶段：stt/llm/tts/orchestrator',
    error_code VARCHAR(64) NULL COMMENT '错误代码',
    error_message TEXT NULL COMMENT '错误描述',
    stack_trace MEDIUMTEXT NULL COMMENT '错误堆栈信息',
    occur_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '错误发生时间',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_trace_phase (trace_id, phase),
    KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错误记录表：保存各阶段异常与堆栈信息';

-- 4. STT 阶段表：保存语音识别的流式结果
CREATE TABLE IF NOT EXISTS ai_trace_stt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    trace_id VARCHAR(64) NOT NULL COMMENT '关联会话追踪 ID',
    user_id BIGINT NULL COMMENT '用户 ID',
    round_index INT NOT NULL DEFAULT 0 COMMENT '轮次编号，从 0 开始',
    segment_index INT NOT NULL DEFAULT 0 COMMENT '同轮次内的段索引，从 0 开始',
    engine_name VARCHAR(128) NULL COMMENT '使用的 ASR 引擎名称',
    language VARCHAR(16) NULL COMMENT '语音语言代码，例如 zh/en',
    request_params JSON NULL COMMENT '识别请求参数 JSON',
    response_json JSON NULL COMMENT '识别响应 JSON',
    is_final TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否最终结果',
    recognized_text TEXT NULL COMMENT '识别出的文本内容',
    confidence DECIMAL(5,2) NULL COMMENT '识别置信度（0-100）',
    start_time DATETIME NOT NULL COMMENT '段开始时间',
    end_time DATETIME NULL COMMENT '段结束时间',
    duration_ms BIGINT NULL COMMENT '段耗时（毫秒），由业务代码计算写入',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_trace_round_seg (trace_id, round_index, segment_index),
    KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='STT 语音识别阶段表：记录各段识别汇总信息';

-- 5. LLM 阶段表：保存大模型的调用数据
CREATE TABLE IF NOT EXISTS ai_trace_llm (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    trace_id VARCHAR(64) NOT NULL COMMENT '会话追踪 ID',
    user_id BIGINT NULL COMMENT '用户 ID',
    round_index INT NOT NULL DEFAULT 0 COMMENT '轮次编号，从 0 开始',
    model_name VARCHAR(128) NULL COMMENT '模型名称，例如 qwen3-32b',
    prompt_text LONGTEXT NULL COMMENT '输入 Prompt 内容',
    response_text LONGTEXT NULL COMMENT '模型完整输出文本',
    tool_calls JSON NULL COMMENT '工具调用记录（JSON）',
    input_tokens INT NULL COMMENT '输入 Token 数',
    output_tokens INT NULL COMMENT '输出 Token 数',
    total_tokens INT NULL COMMENT '总 Token 数，由代码写入',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NULL COMMENT '结束时间',
    latency_ms BIGINT NULL COMMENT '响应耗时（毫秒），由代码计算写入',
    finish_reason VARCHAR(32) NULL COMMENT '结束原因：stop/length/tool_call/error',
    request_params JSON NULL COMMENT '请求参数（JSON）',
    response_json JSON NULL COMMENT '响应内容（JSON）',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_trace_round (trace_id, round_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='LLM 阶段表：记录每轮大模型调用的输入输出及耗时';

-- 6. TTS 阶段表：保存语音合成的流式数据
CREATE TABLE IF NOT EXISTS ai_trace_tts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    trace_id VARCHAR(64) NOT NULL COMMENT '会话追踪 ID',
    user_id BIGINT NULL COMMENT '用户 ID',
    round_index INT NOT NULL DEFAULT 0 COMMENT '轮次编号，从 0 开始',
    segment_index INT NOT NULL DEFAULT 0 COMMENT 'TTS 段索引，从 0 开始',
    input_text TEXT NULL COMMENT '待合成文本内容',
    voice_name VARCHAR(64) NULL COMMENT '声音名称',
    engine_name VARCHAR(128) NULL COMMENT 'TTS 引擎名称',
    sample_rate INT NULL COMMENT '采样率（Hz）',
    format VARCHAR(16) NULL COMMENT '音频格式，例如 wav/mp3/pcm',
    output_audio_url TEXT NULL COMMENT '输出音频文件 URL',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NULL COMMENT '结束时间',
    duration_ms BIGINT NULL COMMENT '合成耗时（毫秒），由代码写入',
    request_params JSON NULL COMMENT '请求参数（JSON）',
    response_json JSON NULL COMMENT '响应 JSON',
    remark VARCHAR(255) NULL COMMENT '备注',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_trace_round_seg (trace_id, round_index, segment_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TTS 阶段表：记录语音合成的段级信息';
