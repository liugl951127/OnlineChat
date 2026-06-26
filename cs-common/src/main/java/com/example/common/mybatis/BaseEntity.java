package com.example.common.mybatis;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MyBatis Plus 实体基类
 *
 * <p>所有业务表继承本类，自动获得：
 * <ul>
 *   <li>id（雪花算法自增）</li>
 *   <li>created_at / updated_at（自动填充）</li>
 *   <li>is_deleted（逻辑删除）</li>
 * </ul>
 */
@Data
public abstract class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(value = "is_deleted")
    private Integer deleted = 0;
}