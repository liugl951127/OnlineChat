package com.example.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 富文本消息协议：
 * <ul>
 *   <li>TEXT  —— 普通文本</li>
 *   <li>BILL  —— 账单（每日交易金额账单）</li>
 *   <li>PRODUCT —— 产品卡片（购买、收益率）</li>
 *   <li>CARD —— 通用图文卡片</li>
 *   <li>CHART —— 图表数据（折线/柱状，由前端渲染）</li>
 * </ul>
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class RichMessage {
    /** TEXT / BILL / PRODUCT / CARD / CHART */
    private String type;
    /** 文本内容（TEXT 时为消息体；其他类型为标题/描述）*/
    private String text;
    /** 富文本附加数据 */
    private Map<String, Object> payload;
    /** 业务事件（TRADE_PROMPT / TRADE_RESULT / BILL_QUERY / ...） */
    private String event;

    // ============ 静态构造 ============
    public static RichMessage text(String text) {
        return new RichMessage("TEXT", text, null, null);
    }
    public static RichMessage bill(String title, List<Map<String, Object>> items) {
        return new RichMessage("BILL", title, Map.of("items", items), "BILL_QUERY");
    }
    public static RichMessage product(String name, String desc, double rate, String period) {
        return new RichMessage("PRODUCT",
                name,
                Map.of("name", name, "desc", desc, "rate", rate, "period", period),
                "PRODUCT_DETAIL");
    }
    public static RichMessage card(String title, String desc, String imageUrl, String linkUrl) {
        return new RichMessage("CARD", title,
                Map.of("title", title, "desc", desc, "imageUrl", imageUrl, "linkUrl", linkUrl),
                null);
    }
    public static RichMessage chart(String title, List<String> labels, List<Double> data) {
        return new RichMessage("CHART", title,
                Map.of("labels", labels, "data", data),
                null);
    }
}