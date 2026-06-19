package com.xingwang.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class DifyService {

    @Value("${dify.base-url}")
    private String difyBaseUrl;

    @Value("${dify.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String uploadFile(MultipartFile file, String user) throws IOException {
        String url = difyBaseUrl + "/v1/files/upload";
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Authorization", "Bearer " + apiKey);

            HttpEntity entity = MultipartEntityBuilder.create()
                    .addBinaryBody("file", file.getInputStream(), 
                            org.apache.http.entity.ContentType.parse(file.getContentType()), file.getOriginalFilename())
                    .addTextBody("user", user)
                    .build();

            httpPost.setEntity(entity);

            HttpResponse response = httpClient.execute(httpPost);
            HttpEntity responseEntity = response.getEntity();
            
            if (responseEntity != null) {
                String responseBody = EntityUtils.toString(responseEntity);
                log.info("File upload response: {}", responseBody);
                
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                if (jsonNode.has("id")) {
                    return jsonNode.get("id").asText();
                }
                throw new RuntimeException("File upload failed: " + responseBody);
            }
            throw new RuntimeException("File upload failed: No response");
        }
    }

    public String sendChatMessage(String query, String user, String fileId, String conversationId) throws IOException {
        String url = difyBaseUrl + "/v1/chat-messages";
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Authorization", "Bearer " + apiKey);
            httpPost.setHeader("Content-Type", "application/json");

            Map<String, Object> body = new HashMap<>();
            body.put("inputs", new HashMap<>());
            body.put("query", query);
            body.put("response_mode", "streaming");
            body.put("conversation_id", conversationId != null ? conversationId : "");
            body.put("user", user);
            
            if (fileId != null && !fileId.isEmpty()) {
                Map<String, String> file = new HashMap<>();
                file.put("type", "image");
                file.put("transfer_method", "local_file");
                file.put("upload_file_id", fileId);
                body.put("files", new Object[]{file});
            }

            String jsonBody = objectMapper.writeValueAsString(body);
            httpPost.setEntity(new org.apache.http.entity.StringEntity(jsonBody, 
                    org.apache.http.entity.ContentType.APPLICATION_JSON));

            HttpResponse response = httpClient.execute(httpPost);
            HttpEntity responseEntity = response.getEntity();
            
            if (responseEntity != null) {
                String responseBody = EntityUtils.toString(responseEntity);
                log.info("Chat message response: {}", responseBody);
                return responseBody;
            }
            throw new RuntimeException("Chat message failed: No response");
        }
    }
}
