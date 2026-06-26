package com.example.im.controller;

import com.example.common.ApiResponse;
import com.example.im.domain.UploadFile;
import com.example.im.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/im/upload")
@RequiredArgsConstructor
public class UploadController {

    private final FileUploadService fileUploadService;

    @PostMapping("/file")
    public ApiResponse<UploadFile> uploadFile(@RequestParam("file") MultipartFile file,
                                               @RequestParam Long sessionId) {
        return ApiResponse.ok(fileUploadService.upload(sessionId, file));
    }
}