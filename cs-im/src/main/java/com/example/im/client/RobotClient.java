package com.example.im.client;

import com.example.common.ApiResponse;
import com.example.common.RichMessage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("cs-robot")
public interface RobotClient {
    @PostMapping("/robot/chat")
    ApiResponse<RichMessage> chat(@RequestBody ChatReq req);

    @GetMapping("/robot/greeting")
    ApiResponse<RichMessage> greeting();

    class ChatReq {
        public String customerId;
        public String text;
        public ChatReq(String c, String t) { this.customerId = c; this.text = t; }
    }
}