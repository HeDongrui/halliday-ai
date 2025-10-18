package com.halliday.ai.trace.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公共追踪实体，统一保存 traceId/userId/roundIndex 等字段。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
public abstract class BaseTraceEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 自增主键。
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 全链路追踪 ID。
     */
    private String traceId;

    /**
     * 关联用户 ID。
     */
    private Long userId;

    /**
     * 对话轮次编号，从 0 开始。
     */
    private Integer roundIndex;

    /**
     * 备注信息。
     */
    private String remark;

    /**
     * 创建时间。
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}
