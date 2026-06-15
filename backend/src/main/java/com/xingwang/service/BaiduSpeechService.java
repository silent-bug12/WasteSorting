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
public class BaiduSpeechService {

    private static final Logger log = LoggerFactory.getLogger(BaiduSpeechService.class);

    private final String apiKey;
    private final String secretKey;

    public BaiduSpeechService() {
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

    public String speechToText(byte[] audioData) throws IOException {
        String token = getAccessToken();
        String cuid = UUID.randomUUID().toString();

        byte[] pcmData = convertWebmToPcm(audioData);

        JSONObject requestBody = new JSONObject();
        requestBody.put("format", "pcm");
        requestBody.put("rate", 16000);
        requestBody.put("channel", 1);
        requestBody.put("cuid", cuid);
        requestBody.put("token", token);
        requestBody.put("len", pcmData.length);
        requestBody.put("speech", java.util.Base64.getEncoder().encodeToString(pcmData));

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, requestBody.toString());

        Request request = new Request.Builder()
                .url("https://vop.baidu.com/server_api")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("语音识别请求失败: " + response);
            }

            String responseBody = response.body().string();
            log.info("百度语音识别响应: {}", responseBody);

            JSONObject jsonObject = new JSONObject(responseBody);
            int errNo = jsonObject.getInt("err_no");
            
            if (errNo == 0) {
                org.json.JSONArray resultArray = jsonObject.getJSONArray("result");
                String result = "";
                if (resultArray.length() > 0) {
                    result = resultArray.getString(0);
                }
                return result;
            } else {
                String errMsg = jsonObject.getString("err_msg");
                log.error("语音识别失败: {} - {}", errNo, errMsg);
                throw new RuntimeException("语音识别失败: " + errMsg);
            }
        }
    }

    private byte[] convertWebmToPcm(byte[] webmData) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-i", "pipe:0",
                    "-f", "s16le",
                    "-ar", "16000",
                    "-ac", "1",
                    "-"
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();

            OutputStream stdin = process.getOutputStream();
            InputStream stdout = process.getInputStream();

            stdin.write(webmData);
            stdin.close();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            
            while ((bytesRead = stdout.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("ffmpeg转换失败，退出码: {}", exitCode);
                return webmData;
            }

            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("ffmpeg不可用，使用原始数据: {}", e.getMessage());
            return webmData;
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
