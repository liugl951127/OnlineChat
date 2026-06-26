package com.example.im.client;

import com.example.common.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient("cs-trade")
public interface TradeClient {
    @GetMapping("/trade/bills/recent")
    ApiResponse<Object> recentBills(@RequestParam String customerId,
                                    @RequestParam(defaultValue = "7") int days);

    @GetMapping("/trade/products")
    ApiResponse<Object> products(@RequestParam(required = false) String keyword);
}