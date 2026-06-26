package com.example.im.service;

import com.example.common.RichMessage;
import com.example.im.domain.Faq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 机器人应答引擎（v1.9.0：集成 FAQ 知识库）
 *
 * <p>应答优先级：
 * <ol>
 *   <li>关键词命中（人工 / 账单 / 产品）</li>
 *   <li>FAQ 知识库检索（MyBatis Plus LIKE 搜索）</li>
 *   <li>LLM 兜底（如启用 OpenAI / 通义千问）</li>
 *   <li>默认回复 + 转人工引导</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RobotEngine {

    /** FAQ 业务（v1.9.0 新增集成） */
    private final FaqService faqService;

    /** LLM API Key（未配置时降级到默认回复） */
    @Value("${cs.llm.api-key:}")
    private String llmApiKey;

    /**
     * 处理用户输入，返回富文本应答
     *
     * @param customerId 客户 ID（用于个性化）
     * @param text       用户输入
     * @return RichMessage（卡片 / 文本 / 产品 / 按钮）
     */
    public RichMessage handle(String customerId, String text) {
        // 1) 入参保护
        if (text == null) text = "";
        String t = text.toLowerCase().trim();

        // 2) 关键词命中
        RichMessage hit = matchKeyword(t);
        if (hit != null) return hit;

        // 3) FAQ 知识库检索
        if (t.length() >= 2) {
            List<Faq> faqs = faqService.search(t);
            if (!faqs.isEmpty()) {
                Faq top = faqs.get(0);  // 取 top 1
                faqService.incrementView(top.getId());  // 浏览 +1
                log.info("[Robot] 命中 FAQ id={} question={}", top.getId(), top.getQuestion());
                return RichMessage.card(
                        "💡 " + top.getQuestion(),
                        top.getAnswer(),
                        "",
                        "cs://faq/" + top.getId()
                );
            }
        }

        // 4) LLM 兜底（如启用）
        if (!llmApiKey.isBlank()) {
            return callLlm(text);
        }

        // 5) 默认回复 + 转人工引导
        return RichMessage.text(
                "抱歉没理解你的问题。可以试试：\n" +
                        "• 「查账单」查看最近交易\n" +
                        "• 「产品」查看理财产品\n" +
                        "• 「人工」转接坐席\n" +
                        "• 顶部菜单浏览「常见问题」"
        );
    }

    /**
     * 关键词匹配
     */
    private RichMessage matchKeyword(String t) {
        // 转人工
        if (t.contains("人工") || t.contains("坐席") || t.contains("客服") || t.contains("转接")) {
            return RichMessage.card(
                    "🎧 即将为你转人工坐席",
                    "点击下方按钮一键转接\n坐席服务时间：9:00-22:00",
                    "",
                    "cs://transfer-to-agent"
            );
        }

        // 查账单
        if (t.contains("账单") || t.contains("交易") || t.contains("流水")) {
            return RichMessage.text("正在为你查询最近 7 天的交易账单，请稍候...");
        }

        // 产品 / 收益率
        if (t.contains("产品") || t.contains("理财") || t.contains("收益率") || t.contains("收益")) {
            return RichMessage.product(
                    "活期理财 Plus",
                    "稳健收益，T+0 赎回",
                    2.85,
                    "灵活"
            );
        }

        // 风险评估
        if (t.contains("风险评估") || t.contains("评估") || t.contains("问卷")) {
            return RichMessage.card(
                    "📊 风险评估",
                    "完成 5 题问卷，评估您的风险承受能力（1 年内有效）",
                    "",
                    "cs://risk-assess"
            );
        }

        // 购买金融产品
        if (t.contains("购买") || t.contains("下单") || t.contains("买入")) {
            return RichMessage.card(
                    "🛍️ 金融产品购买",
                    "查看在售的金融产品，支持保险/理财/基金/国债",
                    "",
                    "cs://product-list"
            );
        }

        // 开户
        if (t.contains("开户") || t.contains("注册")) {
            return RichMessage.card(
                    "📝 在线开户",
                    "5 分钟在线开户，支持身份证 + 人脸识别",
                    "",
                    "cs://open-account"
            );
        }

        // 帮助
        if (t.contains("帮助") || t.contains("help")) {
            return RichMessage.text(
                    "我是智能客服小助手，可以帮你：\n" +
                            "1️⃣ 查询账单 / 交易\n" +
                            "2️⃣ 浏览理财产品\n" +
                            "3️⃣ 风险评估\n" +
                            "4️⃣ 转接人工客服\n" +
                            "5️⃣ 创建工单（投诉/建议）\n" +
                            "6️⃣ 知识库问答"
            );
        }

        return null;  // 未命中
    }

    /**
     * LLM 兜底（mock 实现）
     */
    private RichMessage callLlm(String text) {
        // 实际生产：调用 OpenAI / 通义千问 API
        // 这里返回 mock
        log.info("[Robot] LLM mock: {}", text);
        return RichMessage.text("[LLM 兜底] 您说的是：" + text);
    }
}