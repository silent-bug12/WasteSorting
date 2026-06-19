package com.xingwang.service;

import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class BaiduTextToSpeechService {

    private static final Logger log = LoggerFactory.getLogger(BaiduTextToSpeechService.class);

    private final String apiKey;
    private final String secretKey;

    public BaiduTextToSpeechService() {
        this.apiKey = System.getenv("BAIDU_API_KEY");
        this.secretKey = System.getenv("BAIDU_SECRET_KEY");
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("BAIDU_API_KEY 环境变量未设置，使用默认值");
        } else {
            log.info("已读取 BAIDU_API_KEY");
        }
        
        if (secretKey == null || secretKey.isEmpty()) {
            log.warn("BAIDU_SECRET_KEY 环境变量未设置，使用默认值");
        } else {
            log.info("已读取 BAIDU_SECRET_KEY");
        }
    }

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient()
            .newBuilder()
            .readTimeout(300, TimeUnit.SECONDS)
            .build();

    private String accessToken = null;
    private long tokenExpireTime = 0;

    public byte[] textToSpeech(String text) throws IOException {
        String token = getAccessToken();
        String cuid = UUID.randomUUID().toString();

        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        String params = String.format(
                "tok=%s&cuid=%s&ctp=1&lan=zh&spd=5&pit=5&vol=5&per=4&aue=3&tex=%s",
                token,
                cuid,
                java.net.URLEncoder.encode(text, "UTF-8")
        );

        RequestBody body = RequestBody.create(mediaType, params);

        Request request = new Request.Builder()
                .url("https://tsn.baidu.com/text2audio")
                .method("POST", body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Accept", "*/*")
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("文字转语音请求失败: " + response);
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("响应体为空");
            }

            byte[] audioData = responseBody.bytes();
            String contentType = response.header("Content-Type", "");

            if (contentType.contains("audio")) {
                return audioData;
            } else {
                String responseText = new String(audioData);
                log.error("文字转语音失败: {}", responseText);
                throw new RuntimeException("文字转语音失败: " + responseText);
            }
        }
    }

    private synchronized String getAccessToken() throws IOException {
        long now = System.currentTimeMillis();
        
        if (accessToken != null && now < tokenExpireTime) {
            return accessToken;
        }

        String currentApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : "URS89mUAcgsWH0GXoXadc4Kn";
        String currentSecretKey = (secretKey != null && !secretKey.isEmpty()) ? secretKey : "zpKHs6w3vAMGOg4RYbRqzOddagzChLn3";

        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, 
                "grant_type=client_credentials&client_id=" + currentApiKey + "&client_secret=" + currentSecretKey);

        Request request = new Request.Builder()
                .url("https://aip.baidubce.com/oauth/2.0/token")
                .method("POST", body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取AccessToken失败: " + response);
            }

            String responseBody = response.body().string();
            JSONObject jsonObject = new JSONObject(responseBody);
            
            accessToken = jsonObject.getString("access_token");
            int expiresIn = jsonObject.getInt("expires_in");
            tokenExpireTime = now + (expiresIn - 60) * 1000;

            log.info("获取AccessToken成功，有效期: {}秒", expiresIn);
            return accessToken;
        }
    }
}