package com.example.im.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 会话帧快照（v2.2.78）
 *
 * <p>会话期间，每隔一段时间（默认 5 秒）采集一张客户端画面快照 +
 * 用户交互事件（滚动/点击/输入）。会话结束后由 ReplaySynthesizerService
 * 把所有帧合并成 MP4 视频。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code frameKind} - SCREENSHOT(整页截图) / INTERACTION(交互事件) / MESSAGE(消息节点)</li>
 *   <li>{@code imageData} - 服务端未上云时存 base64 编码</li>
 *   <li>{@code imageUrl} - 上云后填 CDN URL，本地 base64 清空</li>
 *   <li>{@code durationMs} - 该帧在视频中持续时长（毫秒），默认 5000</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("replay_frame")
public class ReplayFrame {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 chat_session.id */
    private Long sessionId;

    /** 关联 chat_message.id (可选, 交互/MESSAGE 帧才有) */
    private Long messageId;

    /** 帧序号, 从 0 开始递增 */
    private Integer seq;

    /** 快照时间 (毫秒精度) */
    private LocalDateTime capturedAt;

    /** 相对会话起始偏移 (毫秒) */
    private Long offsetMs;

    /** SCREENSHOT/PAGE/INTERACTION/MESSAGE */
    private String frameKind;

    /** 服务端存储路径 / CDN URL */
    private String imageUrl;

    /** base64 图片数据 (未上云时使用) */
    @TableField(select = false)
    private String imageData;

    /** 图片宽度 */
    private Integer width;

    /** 图片高度 */
    private Integer height;

    /** 该帧持续时长（毫秒） */
    private Integer durationMs;

    /** JSON 元数据 */
    private String metadata;

    /** 上传者 customer/agent */
    private String uploadedBy;

    private LocalDateTime createdAt;

    /** 帧类型常量 */
    public static final String KIND_SCREENSHOT = "SCREENSHOT";
    public static final String KIND_INTERACTION = "INTERACTION";
    public static final String KIND_MESSAGE = "MESSAGE";
    public static final String KIND_PAGE = "PAGE";
}