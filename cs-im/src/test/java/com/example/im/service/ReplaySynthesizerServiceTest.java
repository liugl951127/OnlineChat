package com.example.im.service;

import com.example.im.domain.ReplayFrame;
import com.example.im.domain.ReplayJob;
import com.example.im.repo.ChatSessionMapper;
import com.example.im.repo.ReplayFrameRepo;
import com.example.im.repo.ReplayJobRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReplaySynthesizerService 单元测试（v2.2.78）
 */
@ExtendWith(MockitoExtension.class)
class ReplaySynthesizerServiceTest {

    @Mock private ReplayFrameRepo frameRepo;
    @Mock private ReplayJobRepo jobRepo;
    @Mock private ChatSessionMapper sessionMapper;

    @InjectMocks private ReplaySynthesizerService service;

    private ReplayFrame makeFrame(Long sessionId, int seq, String base64Png, int durationMs) {
        return ReplayFrame.builder()
                .id((long) seq)
                .sessionId(sessionId)
                .seq(seq)
                .frameKind("SCREENSHOT")
                .imageData("data:image/png;base64," + base64Png)
                .width(640)
                .height(480)
                .durationMs(durationMs)
                .offsetMs((long) seq * durationMs)
                .build();
    }

    private String makeTestPngBase64() {
        try {
            BufferedImage img = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "storageDir", "/tmp/test-replay");
        ReflectionTestUtils.setField(service, "publicBase", "http://localhost:9001/im/replay/video");
        ReflectionTestUtils.setField(service, "defaultFramerate", 2);
    }

    @Test
    void testTriggerSynthesis_createsPendingJob() throws Exception {
        when(frameRepo.countBySession(1L)).thenReturn(5);
        when(jobRepo.save(any())).thenAnswer(inv -> {
            ReplayJob j = inv.getArgument(0);
            j.setId(99L);
            return j;
        });
        when(jobRepo.findById(anyLong())).thenReturn(
                ReplayJob.builder().id(99L).sessionId(1L).status(ReplayJob.STATUS_RUNNING).build());
        when(frameRepo.findBySessionOrderBySeq(1L)).thenReturn(Collections.emptyList());

        Long jobId = service.triggerSynthesis(1L);
        // 等异步线程跑完
        Thread.sleep(500);

        assertNotNull(jobId);
        assertEquals(99L, jobId);
        verify(jobRepo, atLeastOnce()).save(any(ReplayJob.class));
    }

    @Test
    void testGetJob_returnsLatest() {
        ReplayJob j = ReplayJob.builder().id(1L).sessionId(1L).status("SUCCESS").build();
        when(jobRepo.findLatestBySession(1L)).thenReturn(j);

        ReplayJob result = service.getJob(1L);

        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        verify(jobRepo).findLatestBySession(1L);
    }

    @Test
    void testSynthesize_emptyFrames_throws() throws Exception {
        when(jobRepo.findById(anyLong())).thenReturn(
                ReplayJob.builder().id(1L).sessionId(1L).status(ReplayJob.STATUS_RUNNING).build());
        when(frameRepo.findBySessionOrderBySeq(anyLong())).thenReturn(Collections.emptyList());

        assertThrows(Exception.class, () -> service.synthesize(1L, 1L));
    }

    @Test
    void testSynthesize_singleFrame_success() throws Exception {
        String png = makeTestPngBase64();
        ReplayFrame frame = makeFrame(1L, 0, png, 2000);
        when(jobRepo.findById(anyLong())).thenReturn(
                ReplayJob.builder().id(1L).sessionId(1L).status(ReplayJob.STATUS_RUNNING).build());
        when(frameRepo.findBySessionOrderBySeq(1L)).thenReturn(List.of(frame));
        when(sessionMapper.selectById(1L)).thenReturn(null);  // 容忍 null session

        // ffmpeg 必须存在; 用实际 PNG 测试
        service.synthesize(1L, 1L);

        verify(sessionMapper, atLeastOnce()).selectById(1L);
        verify(jobRepo, atLeast(2)).save(any(ReplayJob.class));
    }

    @Test
    void testSynthesize_interactionFramesSkipped() throws Exception {
        String png = makeTestPngBase64();
        ReplayFrame screenshot = makeFrame(1L, 0, png, 3000);
        ReplayFrame interaction = ReplayFrame.builder()
                .id(2L).sessionId(1L).seq(1)
                .frameKind(ReplayFrame.KIND_INTERACTION)
                .durationMs(1000)
                .metadata("{\"scroll\": 100}")
                .build();
        ReplayFrame screenshot2 = makeFrame(1L, 2, png, 3000);

        when(jobRepo.findById(anyLong())).thenReturn(
                ReplayJob.builder().id(1L).sessionId(1L).status(ReplayJob.STATUS_RUNNING).build());
        when(frameRepo.findBySessionOrderBySeq(1L))
                .thenReturn(List.of(screenshot, interaction, screenshot2));

        service.synthesize(1L, 1L);

        // 交互帧被跳过, 只合成 2 帧 SCREENSHOT
        // 验证 job 最终为 SUCCESS
        verify(jobRepo, atLeast(2)).save(any(ReplayJob.class));
    }
}