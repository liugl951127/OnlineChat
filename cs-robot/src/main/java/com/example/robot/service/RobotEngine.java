package com.example.robot.service;

import com.example.common.RichMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 机器人应答引擎：内置意图库 + 富文本构造。
 * 支持 LLM 兜底（未配置 LLM key 时降级到默认回复）。
 */
@Slf4j
@Service
public class RobotEngine {

    @Value("${cs.llm.api-key:}")
    private String llmApiKey;

    /** 意图处理 */
    public RichMessage handle(String customerId, String text) {
        if (text == null) text = "";
        String t = text.toLowerCase().trim();

        // 1) 关键词匹配
        RichMessage hit = matchKeyword(t);
        if (hit != null) return hit;

        // 2) LLM 兜底（如启用）
        if (!llmApiKey.isBlank()) {
            return callLlm(text);
        }

        // 3) 默认回复 + 转人工引导
        return RichMessage.text(
                "抱歉没理解你的问题。可以试试：\n" +
                        "• 「查账单」查看最近交易\n" +
                        "• 「产品」查看理财产品\n" +
                        "• 「人工」转接坐席");
    }

    private RichMessage matchKeyword(String t) {
        // 转人工
        if (t.contains("人工") || t.contains("坐席") || t.contains("客服") || t.contains("转接")) {
            return RichMessage.card("即将为你转人工坐席", "点击下方按钮一键转接", "",
                    "cs://transfer-to-agent");
        }

        // 查账单
        if (t.contains("账单") || t.contains("交易记录") || t.contains("流水")) {
            return RichMessage.text("正在为你查询最近 7 天的交易账单...");
        }

        // 产品 / 收益率
        if (t.contains("产品") || t.contains("理财") || t.contains("收益率") || t.contains("收益")) {
            return RichMessage.product("活期理财 Plus",
                    "稳健收益，T+0 赎回", 2.85, "灵活");
        }

        // 开户
        if (t.contains("开户") || t.contains("怎么开")) {
            return RichMessage.text("开户流程：\n1️⃣ 准备身份证 + 银行卡\n2️⃣ 在线填写信息\n3️⃣ 联网核查通过即可。\n需要协助请回复「人工」");
        }

        // 充值 / 提现
        if (t.contains("充值") || t.contains("怎么充")) {
            return RichMessage.text("充值：登录 → 钱包 → 充值 → 选银行卡 → 输入金额。\n单笔限额 5 万，日累计 20 万。");
        }
        if (t.contains("提现") || t.contains("怎么提") || t.contains("取出")) {
            return RichMessage.text("提现：登录 → 钱包 → 提现 → 输入金额。\nT+1 到账，免手续费。");
        }

        // 退款
        if (t.contains("退款") || t.contains("退钱") || t.contains("退订")) {
            return RichMessage.text("退款：订单页 → 申请退款 → 等待审核（1-3 工作日）。\n需要立即处理请回复「人工」。");
        }

        // 投诉
        if (t.contains("投诉") || t.contains("举报")) {
            return RichMessage.text("已记录你的诉求，正在为你转接投诉专员。");
        }

        // 风险
        if (t.contains("安全") || t.contains("诈骗") || t.contains("被盗")) {
            return RichMessage.text("如遇可疑交易请立即挂失：拨打 95XXX 或回复「人工」紧急冻结。");
        }

        return null;
    }

    private RichMessage callLlm(String text) {
        // 占位：实际接入 LLM API
        log.info("[Robot] LLM stub called for: {}", text);
        return RichMessage.text("[LLM 模式] 暂未实现，生产环境替换此方法");
    }

    /** 预置一组欢迎语 */
    public RichMessage greeting() {
        return RichMessage.card("👋 你好，我是智能客服小助手",
                "我可以帮你查账单、查产品、办理简单业务。\n回复「人工」转接坐席。",
                "", "");
    }
}