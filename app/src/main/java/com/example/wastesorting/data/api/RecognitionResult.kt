package com.example.wastesorting.data.api

/**
 * 垃圾识别 API 接口
 *
 * 后端服务需要实现以下接口：
 *
 * POST /api/recognize
 * Content-Type: multipart/form-data
 *
 * 请求参数:
 *   - image: 图片文件 (JPEG, multipart)
 *
 * 响应格式 (JSON):
 * {
 *   "success": true,
 *   "item_name": "一次性快餐盒",
 *   "category": "其他垃圾",
 *   "confidence": 0.95
 * }
 *
 * 错误响应:
 * {
 *   "success": false,
 *   "error": "无法识别图片中的物品"
 * }
 */

data class RecognitionResult(
    val success: Boolean,
    val itemName: String?,
    val category: String?,
    val confidence: Float?,
    val error: String?
) {
    companion object {
        fun error(msg: String) = RecognitionResult(
            success = false,
            itemName = null,
            category = null,
            confidence = null,
            error = msg
        )
    }
}
