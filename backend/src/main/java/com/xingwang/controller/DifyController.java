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
            String conversationId = request.get("conversation_id");
            
            String response = difyService.sendChatMessage(query, user, null, conversationId);
            
            // 从 SSE 响应中提取 conversation_id
            String extractedConvId = extractConversationId(response, conversationId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("response", response);
            result.put("conversation_id", extractedConvId);
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
            @RequestParam("query") String query,
            @RequestParam(value = "conversation_id", required = false) String conversationId) {
        try {
            String fileUrl = difyService.uploadFile(file, user);
            String chatResponse = difyService.sendChatMessage(query, user, fileUrl, conversationId);
            
            // 从 SSE 响应中提取 conversation_id
            String extractedConvId = extractConversationId(chatResponse, conversationId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("fileUrl", fileUrl);
            response.put("chatResponse", chatResponse);
            response.put("conversation_id", extractedConvId);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Upload and chat failed", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 从 Dify SSE 响应中提取 conversation_id
     */
    private String extractConversationId(String responseBody, String fallback) {
        try {
            // SSE 格式: data: {"event":"message","conversation_id":"xxx",...}
            for (String line : responseBody.split("\n")) {
                if (line.startsWith("data: ")) {
                    String json = line.substring(6).trim();
                    if (json.startsWith("{")) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper =
                                new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(json);
                        if (node.has("conversation_id") && !node.get("conversation_id").asText().isEmpty()) {
                            return node.get("conversation_id").asText();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract conversation_id from response", e);
        }
        return fallback;
    }
}
