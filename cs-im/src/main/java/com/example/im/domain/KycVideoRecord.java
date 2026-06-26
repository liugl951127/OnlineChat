package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * KYC 双录视频（cs_im.kyc_video_record 表）
 *
 * <p>每个客户对每个声明录制一段视频，存储到 OSS / S3 / 本地。
 */
@Data
@TableName("kyc_video_record")
public class KycVideoRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联申请单 ID */
    private Long applicationId;

    /** 关联声明 ID */
    private Long statementId;

    /** 段序号（同一申请单可能录制多段） */
    private Integer segmentNo;

    /** 视频 URL（OSS / S3 / 本地） */
    private String videoUrl;

    /** 时长（秒） */
    private Integer durationSec;

    /** 文件大小（KB） */
    private Integer fileSizeKb;

    /** SHA-256 校验 */
    private String checksum;

    /** 录制时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime recordedAt;

    @TableLogic
    private Integer deleted;
}