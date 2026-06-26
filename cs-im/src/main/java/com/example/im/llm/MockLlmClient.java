package com.example.im.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mock LLM 客户端 — 关键词模板匹配（无需真实 API）
 *
 * <p>支持场景：
 * <ul>
 *   <li>投诉类（"投诉"/"差评"/"骗人"）→ 道歉 + 升级</li>
 *   <li>理财类（"理财"/"基金"/"收益"）→ 产品推荐</li>
 *   <li>转账类（"转账"/"到账"/"余额"）→ 引导操作</li>
 *   <li>KYC 类（"实名"/"认证"/"KYC"）→ 流程引导</li>
 *   <li>密码类（"密码"/"登录不上"）→ 找回流程</li>
 * </ul>
 */
@Slf4j
@Component
public class MockLlmClient implements LlmClient {

    private record Rule(String[] keywords, String reply) {}

    private static final List<Rule> RULES = List.of(
        new Rule(new String[]{"投诉", "差评", "骗人", "没用", "气死"},
            "非常抱歉给您带来困扰，我理解您现在的心情。我会立即为您升级处理，请稍等片刻，您也可以通过工单系统提交详细情况，我们会优先处理。"),
        new Rule(new String[]{"理财", "收益", "利率", "投资", "基金"},
            "我们有几款不错的理财产品：1) 稳赢 30 天（年化 3.2%）；2) 稳赢 90 天（年化 3.8%）；3) 成长基金（5 年期）。请问您想了解哪一款？"),
        new Rule(new String[]{"转账", "到账", "余额", "钱"},
            "请稍等，我帮您查询一下。您也可以在 我的 页面查看账户余额。如果转账失败，常见原因有：1) 余额不足；2) 单笔超 5 万限额；3) KYC 未完成。"),
        new Rule(new String[]{"实名", "认证", "KYC", "身份证"},
            "KYC 实名认证可在客户页面 工具栏-实名认证 入口完成。整个流程约 5 分钟：身份证 OCR → 活体检测 → 人脸比对 → 视频双录 → 坐席审核 → 银行卡四要素。"),
        new Rule(new String[]{"密码", "登录不上", "忘记", "找回"},
            "您可以在登录页面点击 忘记密码，通过预留手机号验证码重置。需要您的账号已完成 KYC 认证。如有其他问题请告诉我。"),
        new Rule(new String[]{"你好", "您好", "hi", "hello"},
            "您好，我是您的专属客服小助手，请问有什么可以帮您？"),
        new Rule(new String[]{"再见", "拜拜", "byebye"},
            "感谢您的咨询，祝您生活愉快！如有问题随时联系我们。")
    );

    @Override
    public String chat(String systemPrompt, String userMessage) {
        log.debug("[MockLlm] system={}, user={}", systemPrompt, userMessage);
        return matchRule(userMessage);
    }

    @Override
    public String chat(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return "请告诉我您需要什么帮助？";
        ChatMessage last = messages.get(messages.size() - 1);
        return matchRule(last.content());
    }

    private String matchRule(String text) {
        if (text == null || text.isBlank()) return "请告诉我您的问题，我会尽力帮您解决。";
        String lower = text.toLowerCase();
        for (Rule rule : RULES) {
            for (String kw : rule.keywords) {
                if (lower.contains(kw.toLowerCase())) {
                    return rule.reply;
                }
            }
        }
        return "您的问题我已记录，正在为您查询。请稍等片刻，也可以留下您的联系方式，我们会有专人回访您。";
    }
}