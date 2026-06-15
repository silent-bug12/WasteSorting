package com.example.wastesorting.data.api

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AI 识别 HTTP 客户端
 *
 * 对接后端 Dify 服务，实现图片分析、语音转文字、文字转语音。
 *
 * 服务器地址配置说明：
 *
 * 1. 模拟器测试：使用 http://10.0.2.2:8080（映射到宿主机 localhost）
 * 2. 真机 + 本地电脑：使用电脑的局域网 IP，如 http://192.168.x.x:8080
 *    （确保手机和电脑在同一 WiFi 网络，电脑防火墙开放 8080 端口）
 * 3. 云服务器部署：使用服务器的公网 IP 或域名，如 http://your-server-ip:8080
 *    （确保云服务器安全组/防火墙开放 8080 端口）
 */
object AiRecognitionClient {

    /**
     * 后端服务地址，根据部署环境修改此处：
     * - 模拟器测试：http://10.0.2.2:8080
     * - 真机测试（本地服务器）：http://192.168.x.x:8080（替换为实际 IP）
     * - 云服务器部署：http://your-public-ip-or-domain:8080
     */
    var baseUrl: String = "http://192.168.0.100:8080"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 上传图片并调用 AI 对话分析
     *
     * @param bitmap 输入图片
     * @param query 用户查询文字（如"这是什么垃圾？"）
     * @return AI 分析文本
     */
    suspend fun uploadAndChat(bitmap: Bitmap, query: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            val imageBytes = stream.toByteArray()

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "photo.png",
                    imageBytes.toRequestBody("image/png".toMediaType())
                )
                .addFormDataPart("user", "android-user")
                .addFormDataPart("query", query.ifEmpty { "分析这张图片" })
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/dify/upload-and-chat")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("服务返回错误: ${response.code}"))
            }

            val bodyString = response.body?.string() ?: ""
            Log.d(TAG, "原始响应长度: ${bodyString.length}")
            Log.d(TAG, "原始响应前500字符: ${bodyString.take(500)}")

            val answer = extractAnswerFromResponse(bodyString)
            Log.d(TAG, "提取结果: $answer")

            Result.success(answer)
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "无法连接 AI 服务", e)
            Result.failure(IOException("无法连接 AI 服务，请确认服务已启动"))
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "AI 服务超时", e)
            Result.failure(IOException("AI 服务响应超时，请重试"))
        } catch (e: Exception) {
            Log.e(TAG, "上传分析失败", e)
            Result.failure(e)
        }
    }

    /**
     * 语音转文字
     *
     * @param audioFile 录音文件
     * @return 识别出的文字
     */
    suspend fun speechToText(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "audio",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/speech-to-text")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("语音识别服务返回错误: ${response.code}"))
            }

            val json = JSONObject(response.body?.string() ?: "{}")
            val text = json.optString("text", "无法识别语音内容")
            Result.success(text)
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "无法连接语音识别服务", e)
            Result.failure(IOException("语音识别服务不可用"))
        } catch (e: Exception) {
            Log.e(TAG, "语音识别失败", e)
            Result.failure(e)
        }
    }

    /**
     * 文字转语音
     *
     * @param text 要朗读的文字
     * @return 音频字节数组
     */
    suspend fun textToSpeech(text: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().put("text", text).toString()
            val request = Request.Builder()
                .url("$baseUrl/api/text-to-speech")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("TTS 服务返回错误: ${response.code}"))
            }

            val bytes = response.body?.bytes() ?: byteArrayOf()
            Result.success(bytes)
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "无法连接 TTS 服务", e)
            Result.failure(IOException("语音合成服务不可用"))
        } catch (e: Exception) {
            Log.e(TAG, "TTS 失败", e)
            Result.failure(e)
        }
    }

    /**
     * 解析 SSE 流式响应，提取 agent_message 中的 answer
     */
    private fun extractAnswerFromResponse(response: String): String {
        val answers = mutableListOf<String>()
        
        // 检查是否是 JSON 格式（包含 chatResponse 字段）
        var sseContent = response
        try {
            val json = JSONObject(response)
            if (json.has("chatResponse")) {
                sseContent = json.getString("chatResponse")
                Log.d(TAG, "从 JSON 中提取 chatResponse 字段")
            } else if (json.has("answer")) {
                // 直接是普通 JSON 响应
                val directAnswer = json.getString("answer")
                if (directAnswer.isNotEmpty()) {
                    return directAnswer
                }
            }
        } catch (e: Exception) {
            // 不是 JSON 格式，使用原始内容
            Log.d(TAG, "响应不是 JSON 格式，使用原始内容")
        }

        val lines = sseContent.split("\n", "\r\n")
        Log.d(TAG, "解析 SSE 响应，共 ${lines.size} 行")

        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("data:")) {
                try {
                    val dataStr = trimmedLine.substring(5).trim()
                    Log.d(TAG, "第 $index 行 data: ${dataStr.take(100)}...")

                    if (dataStr == "[DONE]") {
                        Log.d(TAG, "收到 [DONE] 标记")
                        continue
                    }

                    val json = JSONObject(dataStr)
                    val event = json.optString("event")
                    Log.d(TAG, "事件类型: $event")

                    if (event == "agent_message") {
                        val answer = json.optString("answer", "")
                        Log.d(TAG, "agent_message answer: $answer")
                        if (answer.isNotEmpty()) {
                            answers.add(answer)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析第 $index 行失败: ${e.message}")
                }
            } else if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("event:")) {
                // 尝试直接解析整行（可能是纯 JSON 响应）
                try {
                    val json = JSONObject(trimmedLine)
                    if (json.has("answer")) {
                        val answer = json.getString("answer")
                        if (answer.isNotEmpty()) {
                            answers.add(answer)
                        }
                    }
                } catch (_: Exception) {
                    // 不是 JSON，忽略
                }
            }
        }

        val result = answers.joinToString("")
        Log.d(TAG, "提取结果长度: ${result.length}")
        return result
    }

    private const val TAG = "AiRecognitionClient"
}