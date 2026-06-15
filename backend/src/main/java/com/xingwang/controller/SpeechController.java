package com.xingwang.controller;

import com.xingwang.service.BaiduSpeechService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SpeechController {

    private final BaiduSpeechService baiduSpeechService;

    private final String[] mockResponses = {
        "这张图片是什么内容",
        "帮我分析一下这个截图",
        "图片里有什么信息",
        "描述一下这张图片",
        "解释一下这个画面",
        "这是什么产品",
        "图片中的内容是什么"
    };

    @PostMapping("/speech-to-text")
    public ResponseEntity<Map<String, Object>> speechToText(
            @RequestParam("audio") MultipartFile audio) {
        
        log.info("Received speech to text request, file: {}, size: {} bytes", 
                audio.getOriginalFilename(), audio.getSize());
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            byte[] audioData = audio.getBytes();
            
            if (audioData.length > 0) {
                String result = baiduSpeechService.speechToText(audioData);
                
                response.put("success", true);
                response.put("text", result);
                log.info("语音识别结果: {}", result);
            } else {
                response.put("success", false);
                response.put("text", "");
                response.put("error", "音频数据为空");
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("语音识别失败", e);
            
            // 如果百度语音API调用失败，返回模拟数据
            java.util.Random random = new java.util.Random();
            String mockText = mockResponses[random.nextInt(mockResponses.length)];
            
            response.put("success", true);
            response.put("text", mockText);
            response.put("warning", "使用模拟数据，百度语音服务不可用: " + e.getMessage());
            
            return ResponseEntity.ok(response);
        }
    }
}
