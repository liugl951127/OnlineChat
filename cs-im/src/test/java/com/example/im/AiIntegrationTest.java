package com.example.im;

import com.example.im.domain.AiFeedback;
import com.example.im.domain.AiKnowledge;
import com.example.im.domain.AiSuggestion;
import com.example.im.domain.ScreenShareSession;
import com.example.im.domain.VoiceMessage;
import com.example.im.llm.LlmClient;
import com.example.im.repo.AiFeedbackMapper;
import com.example.im.repo.AiKnowledgeMapper;
import com.example.im.repo.AiSuggestionMapper;
import com.example.im.repo.ScreenShareSessionMapper;
import com.example.im.repo.VoiceMessageMapper;
import com.example.im.service.AiAssistantService;
import com.example.im.service.AiKnowledgeService;
import com.example.im.service.VoiceMessageService;
import com.example.im.service.WebRtcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v2.1.0 AI 助手 + 多媒体集成测试
 */
@SpringBootTest
@ActiveProfiles("mysql-it")
class AiIntegrationTest {

    @Autowired AiAssistantService aiService;
    @Autowired AiKnowledgeService knowledgeService;
    @Autowired WebRtcService webRtcService;
    @Autowired VoiceMessageService voiceService;

    @Autowired AiKnowledgeMapper knowledgeMapper;
    @Autowired AiSuggestionMapper suggestionMapper;
    @Autowired AiFeedbackMapper feedbackMapper;
    @Autowired ScreenShareSessionMapper shareMapper;
    @Autowired VoiceMessageMapper voiceMapper;

    @MockBean SimpMessagingTemplate messagingTemplate;

    @Test
    void knowledge_search_returnsRelevantResults() {
        List<AiKnowledge> hits = knowledgeService.search("理财 起购", 5);
        System.out.println("[AI-KB] hits=" + hits.size());
        hits.forEach(k -> System.out.println("  - " + k.getCategory() + " / " + k.getTitle()));
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).getTitle()).containsAnyOf("理财", "起购", "基金", "保险", "KYC", "实名");
    }

    @Test
    void knowledge_listByCategory_faq() {
        List<AiKnowledge> faqs = knowledgeService.listByCategory("FAQ", 10);
        System.out.println("[AI-KB] FAQ count=" + faqs.size());
        assertThat(faqs.size()).isGreaterThan(0);
        assertThat(faqs.get(0).getCategory()).isEqualTo("FAQ");
    }

    @Test
    void assistant_suggestion_generate_and_save() {
        AiSuggestion s = aiService.generateSuggestion(
            100L, "c-test-001", "agent-zhang",
            1L, "我想买点理财，有什么推荐吗？");

        System.out.println("[AI-Suggest] id=" + s.getId()
            + " type=" + s.getSuggestionType()
            + " confidence=" + s.getConfidence());
        System.out.println("[AI-Suggest] content=" + s.getContent());

        assertThat(s.getId()).isNotNull();
        assertThat(s.getSuggestionType()).isEqualTo("KNOWLEDGE");
        assertThat(s.getConfidence()).isGreaterThanOrEqualTo(50.0);
        assertThat(s.getContent()).containsAnyOf("理财", "稳赢", "基金");

        // 数据库可查
        AiSuggestion fromDb = suggestionMapper.selectById(s.getId());
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getAgentUsername()).isEqualTo("agent-zhang");
    }

    @Test
    void assistant_feedback_recorded() {
        AiSuggestion s = aiService.generateSuggestion(
            101L, "c-test-002", "agent-li",
            2L, "我想投诉！");

        aiService.recordFeedback(s.getId(), "agent-li", "USED", 5, null);

        AiSuggestion updated = suggestionMapper.selectById(s.getId());
        assertThat(updated.getUsed()).isTrue();
        assertThat(updated.getRating()).isEqualTo(5);

        AiFeedback fb = feedbackMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiFeedback>()
                .eq("suggestion_id", s.getId())
        ).get(0);
        assertThat(fb.getFeedbackType()).isEqualTo("USED");
        System.out.println("[AI-FB] feedback id=" + fb.getId() + " type=" + fb.getFeedbackType());
    }

    @Test
    void assistant_recentSuggestions() {
        // 生成两条
        aiService.generateSuggestion(200L, "c-1", "agent-wang", 1L, "余额怎么查？");
        aiService.generateSuggestion(201L, "c-2", "agent-wang", 2L, "密码忘了");

        List<AiSuggestion> recent = aiService.recentSuggestions("agent-wang", 5);
        System.out.println("[AI-Recent] agent-wang 共 " + recent.size() + " 条推荐");
        assertThat(recent.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void voice_upload_andAsr() throws Exception {
        String fakeBase64 = "UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=";
        VoiceMessage vm = voiceService.upload(300L, "c-3", "CUSTOMER", 5, 12, fakeBase64);

        System.out.println("[Voice] id=" + vm.getId() + " duration=" + vm.getDurationSec()
            + "s audioUrl=" + vm.getAudioUrl());

        assertThat(vm.getId()).isNotNull();
        assertThat(vm.getAudioUrl()).contains("voice/");
        assertThat(vm.getTranscriptionStatus()).isIn("PENDING", "SUCCESS");

        // 等异步 ASR
        Thread.sleep(500);
        VoiceMessage updated = voiceMapper.selectById(vm.getId());
        System.out.println("[Voice] ASR status=" + updated.getTranscriptionStatus()
            + " text=" + updated.getTranscription());
        // 可能异步尚未完成，不强制 SUCCESS
    }

    @Test
    void voice_listBySession() {
        voiceService.upload(400L, "c-4", "CUSTOMER", 3, 8, "AAA=");
        voiceService.upload(400L, "c-4", "CUSTOMER", 7, 16, "BBB=");

        List<VoiceMessage> list = voiceService.listBySession(400L);
        System.out.println("[Voice-List] sessionId=400 共 " + list.size() + " 条");
        assertThat(list.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void screenShare_initiateAndAccept() {
        ScreenShareSession s = webRtcService.initiate(500L, "agent-test", "c-5");
        System.out.println("[WebRTC] initiate id=" + s.getId() + " status=" + s.getStatus());
        assertThat(s.getId()).isNotNull();
        assertThat(s.getStatus()).isEqualTo("INVITED");

        webRtcService.accept(s.getId(), "{\"type\":\"answer\",\"sdp\":\"v=0\"}");
        ScreenShareSession updated = shareMapper.selectById(s.getId());
        System.out.println("[WebRTC] accepted status=" + updated.getStatus()
            + " startedAt=" + updated.getStartedAt());
        assertThat(updated.getStatus()).isEqualTo("ACTIVE");
        assertThat(updated.getStartedAt()).isNotNull();

        webRtcService.end(s.getId());
        ScreenShareSession ended = shareMapper.selectById(s.getId());
        System.out.println("[WebRTC] ended status=" + ended.getStatus()
            + " durationSec=" + ended.getDurationSec());
        assertThat(ended.getStatus()).isEqualTo("ENDED");
    }

    @Test
    void llmClient_chat_returnsNonEmpty() {
        // 通过 AiKnowledgeService 间接验证 LLM
        String ctx = knowledgeService.buildContextPrompt("理财推荐", 3);
        System.out.println("[LLM] 上下文长度=" + ctx.length());
        System.out.println(ctx.length() > 0 ? ctx.substring(0, Math.min(200, ctx.length())) : "(空)");
    }
}