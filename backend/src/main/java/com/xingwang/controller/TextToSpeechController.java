package com.xingwang.controller;

import com.xingwang.service.BaiduTextToSpeechService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TextToSpeechController {

    private final BaiduTextToSpeechService baiduTextToSpeechService;

    @PostMapping("/text-to-speech")
    public ResponseEntity<byte[]> textToSpeech(@RequestBody TextToSpeechRequest request) {
        try {
            byte[] audioData = baiduTextToSpeechService.textToSpeech(request.getText());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mp3"));
            headers.setContentLength(audioData.length);
            headers.setContentDispositionFormData("attachment", "speech.mp3");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(audioData);
        } catch (Exception e) {
            log.error("文字转语音失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    public static class TextToSpeechRequest {
        private String text;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}