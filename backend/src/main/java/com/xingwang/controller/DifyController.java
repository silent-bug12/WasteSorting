package com.xingwang.controller;

import com.xingwang.service.DifyService;
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
@RequestMapping("/api/dify")
@RequiredArgsConstructor
public class DifyController {

    private final DifyService difyService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("user") String user) {
        try {
            String url = difyService.uploadFile(file, user);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("url", url);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("File upload failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> sendChatMessage(
            @RequestBody Map<String, String> request) {
        try {
            String query = request.get("query");
            String user = request.get("user");
            String fileUrl = request.get("fileUrl");
            
            String response = difyService.sendChatMessage(query, user, fileUrl);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("response", response);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("Chat message failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/upload-and-chat")
    public ResponseEntity<Map<String, Object>> uploadAndChat(
            @RequestParam("file") MultipartFile file,
            @RequestParam("user") String user,
            @RequestParam("query") String query) {
        try {
            String fileUrl = difyService.uploadFile(file, user);
            String chatResponse = difyService.sendChatMessage(query, user, fileUrl);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileUrl", fileUrl);
            response.put("chatResponse", chatResponse);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Upload and chat failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
