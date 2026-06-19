package com.example.wastesorting.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 垃圾识别 HTTP 客户端
 *
 * 对接训练好的深度学习模型服务。
 * 
 * 服务器地址配置说明：
 * 
 * 1. 模拟器测试：使用 http://10.0.2.2:端口（映射到宿主机 localhost）
 * 2. 真机 + 本地电脑：使用电脑的局域网 IP，如 http://192.168.x.x:端口
 *    （确保手机和电脑在同一 WiFi 网络，电脑防火墙开放对应端口）
 * 3. 云服务器部署：使用服务器的公网 IP 或域名，如 http://your-server-ip:端口
 *    （确保云服务器安全组/防火墙开放对应端口）
 */
object GarbageRecognitionClient {

    /**
     * 模型服务地址，根据部署环境修改此处：
     * - 模拟器测试：http://10.0.2.2:端口
     * - 真机测试（本地服务器）：http://192.168.x.x:端口（替换为实际 IP）
     * - 云服务器部署：http://your-public-ip-or-domain:端口
     */
    var baseUrl: String = "http://10.68.159.147:8081"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 调用模型进行垃圾识别
     *
     * @param imageBytes 图片字节数据 (JPEG)
     * @return RecognitionResult 识别结果
     */
    suspend fun recognize(imageBytes: ByteArray): RecognitionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始调用识别 API: $baseUrl/api/recognize")

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "image",
                    "capture.jpg",
                    imageBytes.toRequestBody("image/jpeg".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/recognize")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            Log.d(TAG, "API 响应: code=${response.code}, body=$body")

            if (!response.isSuccessful) {
                return@withContext RecognitionResult.error("服务返回错误: ${response.code}")
            }

            parseResponse(body)
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "无法连接识别服务", e)
            RecognitionResult.error("无法连接识别服务，请确认服务已启动")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "识别服务超时", e)
            RecognitionResult.error("识别服务超时，请重试")
        } catch (e: Exception) {
            Log.e(TAG, "识别请求失败", e)
            RecognitionResult.error("识别失败: ${e.message}")
        }
    }

    private fun parseResponse(jsonStr: String): RecognitionResult {
        return try {
            val json = JSONObject(jsonStr)
            RecognitionResult(
                success = json.optBoolean("success", false),
                itemName = if (json.has("item_name")) json.getString("item_name") else null,
                category = if (json.has("category")) json.getString("category") else null,
                confidence = json.optDouble("confidence", -1.0)
                    .toFloat().takeIf { it >= 0f },
                error = if (json.has("error")) json.getString("error") else null
            )
        } catch (e: Exception) {
            RecognitionResult.error("解析识别结果失败: ${e.message}")
        }
    }

    /**
     * 将 Bitmap 压缩为 JPEG 字节数组
     */
    fun bitmapToJpegBytes(bitmap: android.graphics.Bitmap, quality: Int = 90): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private const val TAG = "GarbageAPI"
}
