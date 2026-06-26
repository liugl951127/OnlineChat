package com.example.im;

import com.example.common.ApiException;
import com.example.im.domain.ChatSession;
import com.example.im.domain.SessionStatus;
import com.example.im.domain.UploadFile;
import com.example.im.repo.ChatSessionRepo;
import com.example.im.service.FileUploadService;
import com.example.im.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UploadServiceTest {

    @Autowired FileUploadService uploadService;
    @Autowired SessionService sessionService;
    @Autowired ChatSessionRepo sessionRepo;

    @Test
    void uploadImage_success() {
        String cid = "u-" + System.nanoTime();
        ChatSession s = sessionService.getOrCreate(cid);
        sessionService.transferToQueue(cid);
        sessionService.acceptByAgent("agentU", null);

        MockMultipartFile img = new MockMultipartFile(
                "file", "test.png", "image/png", "fake-png-content".getBytes());
        UploadFile uf = uploadService.upload(s.getId(), img);
        assertThat(uf.getContentType()).isEqualTo("IMAGE");
        assertThat(uf.getUrl()).startsWith("/upload/");
        assertThat(uf.getSize()).isEqualTo(img.getSize());
    }

    @Test
    void uploadDocument_success() {
        String cid = "u-" + System.nanoTime();
        ChatSession s = sessionService.getOrCreate(cid);
        sessionService.transferToQueue(cid);
        sessionService.acceptByAgent("agentU", null);

        MockMultipartFile doc = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "fake-pdf-content".getBytes());
        UploadFile uf = uploadService.upload(s.getId(), doc);
        assertThat(uf.getContentType()).isEqualTo("DOCUMENT");
    }

    @Test
    void uploadUnsupportedType_rejected() {
        String cid = "u-" + System.nanoTime();
        ChatSession s = sessionService.getOrCreate(cid);
        sessionService.transferToQueue(cid);
        sessionService.acceptByAgent("agentU", null);

        MockMultipartFile exe = new MockMultipartFile(
                "file", "evil.exe", "application/x-msdownload", "evil".getBytes());
        try {
            uploadService.upload(s.getId(), exe);
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(415);
        }
    }

    @Test
    void upload_emptyFile_rejected() {
        String cid = "u-" + System.nanoTime();
        ChatSession s = sessionService.getOrCreate(cid);
        sessionService.transferToQueue(cid);
        sessionService.acceptByAgent("agentU", null);

        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);
        try {
            uploadService.upload(s.getId(), empty);
            org.junit.jupiter.api.Assertions.fail("应被拒绝");
        } catch (ApiException e) {
            assertThat(e.getCode()).isEqualTo(400);
        }
    }
}