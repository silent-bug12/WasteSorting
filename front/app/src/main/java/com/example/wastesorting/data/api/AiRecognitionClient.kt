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
 * AI 对话结果
 */
data class AiChatResult(
    val text: String,
    val conversationId: String?
)

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
     * - 模拟器测试：http://10.0.2.2:8081
     * - 真机测试（本地服务器）：http://192.168.x.x:8081（替换为实际 IP）
     * - 云服务器部署：http://your-public-ip-or-domain:8081
     */
    var baseUrl: String = "http://10.68.159.147:8081"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 上传图片并调用 AI 对话分析
     *
     * @param bitmap 输入图片
     * @param query 用户查询文字
     * @param conversationId 已有会话 ID（首次为 null）
     * @return AiChatResult（包含文本和 conversationId）
     */
    suspend fun uploadAndChat(bitmap: Bitmap, query: String, conversationId: String? = null): Result<AiChatResult> = withContext(Dispatchers.IO) {
        try {
            val resizedBitmap = resizeBitmap(bitmap, 1024)
            val stream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream)
            val imageBytes = stream.toByteArray()
            stream.close()

            Log.d(TAG, "图片压缩后大小: ${imageBytes.size / 1024}KB")

            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "photo.jpg", imageBytes.toRequestBody("image/jpeg".toMediaType()))
                .addFormDataPart("user", "android-user")
                .addFormDataPart("query", query.ifEmpty { "分析这张图片" })

            // conversation_id 为空时也会有值，让后端也能区分
            if (!conversationId.isNullOrEmpty()) {
                builder.addFormDataPart("conversation_id", conversationId)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/dify/upload-and-chat")
                .post(builder.build())
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("服务返回错误: ${response.code}"))
            }

            val bodyString = response.body?.string() ?: ""
            Log.d(TAG, "原始响应长度: ${bodyString.length}")

            val result = extractChatResult(bodyString)
            Log.d(TAG, "提取结果: text=${result.text.take(100)}, convId=${result.conversationId}")

            Result.success(result)
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
     * 纯文本继续对话（不传图片）
     *
     * @param query 用户问题
     * @param conversationId 会话 ID
     * @return AiChatResult
     */
    suspend fun sendChat(query: String, conversationId: String): Result<AiChatResult> = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("query", query)
                put("user", "android-user")
                put("conversation_id", conversationId)
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl/api/dify/chat")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("对话服务返回错误: ${response.code}"))
            }

            val bodyString = response.body?.string() ?: ""
            val result = extractChatResult(bodyString)
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "对话发送失败", e)
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
    /**
     * 等比例缩小图片，使最长边不超过 maxSize
     */
    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDimension = maxOf(width, height)
        if (maxDimension <= maxSize) return bitmap
        val ratio = maxSize.toFloat() / maxDimension
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun extractChatResult(response: String): AiChatResult {
        var convId: String? = null
        var answer = ""

        // 先解析外层 JSON（后端返回的包装）
        try {
            val outerJson = JSONObject(response)
            if (outerJson.has("conversation_id")) {
                val cid = outerJson.optString("conversation_id", "")
                if (cid.isNotEmpty()) convId = cid
            }
            if (outerJson.has("chatResponse")) {
                // 从 chatResponse 中解析 SSE 流
                val (a, c) = parseSseAnswer(outerJson.getString("chatResponse"), convId)
                answer = a
                if (convId.isNullOrEmpty()) convId = c
            } else if (outerJson.has("response")) {
                val (a, c) = parseSseAnswer(outerJson.getString("response"), convId)
                answer = a
                if (convId.isNullOrEmpty()) convId = c
            } else if (outerJson.has("answer")) {
                answer = outerJson.getString("answer")
            }
        } catch (_: Exception) {
            // 不是 JSON 包装，直接作为 SSE 解析
            val (a, c) = parseSseAnswer(response, null)
            answer = a
            if (convId.isNullOrEmpty()) convId = c
        }

        return AiChatResult(answer, convId)
    }

    /**
     * 解析 SSE 流式响应，提取所有 agent_message 的 answer
     */
    private fun parseSseAnswer(sseContent: String, existingConvId: String?): Pair<String, String?> {
        val answers = mutableListOf<String>()
        var convId = existingConvId
        val lines = sseContent.split("\n", "\r\n")

        for ((index, line) in lines.withIndex()) {
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("data:")) {
                val dataStr = trimmedLine.substring(5).trim()
                if (dataStr == "[DONE]") continue
                try {
                    val json = JSONObject(dataStr)
                    val event = json.optString("event")
                    if (event == "agent_message" || event == "message") {
                        val answer = json.optString("answer", "")
                        if (answer.isNotEmpty()) answers.add(answer)
                        // 提取 conversation_id
                        if (convId.isNullOrEmpty()) {
                            val cid = json.optString("conversation_id", "")
                            if (cid.isNotEmpty()) convId = cid
                        }
                    }
                } catch (_: Exception) {}
            } else if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("event:")) {
                try {
                    val json = JSONObject(trimmedLine)
                    if (json.has("answer")) {
                        val a = json.getString("answer")
                        if (a.isNotEmpty()) answers.add(a)
                    }
                } catch (_: Exception) {}
            }
        }

        return answers.joinToString("") to convId
    }

    private const val TAG = "AiRecognitionClient"
}