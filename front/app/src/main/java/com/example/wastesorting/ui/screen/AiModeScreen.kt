package com.example.wastesorting.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.wastesorting.data.api.AiRecognitionClient
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.util.ImageUtils
import com.example.wastesorting.util.saveAndGetRecordId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

// 将 PCM 数据写入 WAV 文件
fun writeWavFile(outputFile: File, pcmData: ByteArray, dataSize: Int, sampleRate: Int) {
    try {
        val outputStream = FileOutputStream(outputFile)
        
        // WAV 文件头
        val header = ByteArray(44)
        
        // RIFF
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        // 文件大小
        val fileSize = 36 + dataSize
        ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(fileSize)
        
        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        // fmt
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        // fmt chunk size
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        // 格式 (PCM)
        header[20] = 1
        header[21] = 0
        
        // 声道数
        header[22] = 1
        header[23] = 0
        
        // 采样率
        ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate)
        
        // 字节率
        val byteRate = sampleRate * 1 * 2
        ByteBuffer.wrap(header, 28, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate)
        
        // 块对齐
        header[32] = 2
        header[33] = 0
        
        // 位深度
        header[34] = 16
        header[35] = 0
        
        // data
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        // data size
        ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize)
        
        outputStream.write(header)
        outputStream.write(pcmData, 0, dataSize)
        outputStream.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

/**
 * 将纯文本中的 Markdown 标记转为 AnnotatedString
 * 支持: **粗体**, *斜体*, `代码`, ### 标题, - 列表
 */
fun renderMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.split("\n")
    for ((index, line) in lines.withIndex()) {
        if (index > 0) append("\n")
        when {
            // ### 标题
            line.trimStart().startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp)) {
                    append(line.trimStart().removePrefix("### "))
                }
            }
            // ## 标题
            line.trimStart().startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp)) {
                    append(line.trimStart().removePrefix("## "))
                }
            }
            // # 标题
            line.trimStart().startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp)) {
                    append(line.trimStart().removePrefix("# "))
                }
            }
            // - 列表
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                append("  •  ")
                append(parseInline(line.trimStart().drop(2)))
            }
            else -> append(parseInline(line))
        }
    }
}

/** 解析行内标记: **粗体**, *斜体*, `代码` */
private fun parseInline(text: String): AnnotatedString = buildAnnotatedString {
    var remaining = text
    while (remaining.isNotEmpty()) {
        val boldStart = remaining.indexOf("**")
        val italicStart = remaining.indexOf("*")
        val codeStart = remaining.indexOf("`")

        // 找到最先出现的标记
        val candidates = listOfNotNull(
            boldStart.takeIf { it >= 0 }?.let { Triple(it, "**", "bold") },
            italicStart.takeIf { it >= 0 && it != boldStart }?.let { Triple(it, "*", "italic") },
            codeStart.takeIf { it >= 0 }?.let { Triple(it, "`", "code") }
        )

        if (candidates.isEmpty()) {
            append(remaining)
            break
        }

        val (pos, marker, type) = candidates.minBy { it.first }
        // 追加前面的普通文本
        if (pos > 0) append(remaining.substring(0, pos))
        val afterMarker = remaining.substring(pos + marker.length)
        val endPos = afterMarker.indexOf(marker)
        if (endPos >= 0) {
            val inner = afterMarker.substring(0, endPos)
            when (type) {
                "bold" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inner) }
                "italic" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inner) }
                "code" -> withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(inner) }
            }
            remaining = afterMarker.substring(endPos + marker.length)
        } else {
            // 没有闭合标记，按普通文本处理
            append(marker)
            remaining = afterMarker
        }
    }
}

/** 解析 AI 返回的 JSON 结果，返回（显示文本, 原始 JSON 字符串） */
fun parseAiJsonResult(answer: String): Pair<String, String?> {
    // 尝试从 SSE 响应或直接 JSON 中提取 items 数组
    val jsonStr = try {
        // 先尝试直接解析
        val json = org.json.JSONObject(answer)
        if (json.has("items")) answer else null
    } catch (_: Exception) {
        // 不是 JSON，可能在 markdown 代码块中，尝试提取 ```json ... ```
        val codeBlock = """```json\s*([\s\S]*?)```""".toRegex().find(answer)
        codeBlock?.groupValues?.getOrNull(1)?.let {
            try {
                if (org.json.JSONObject(it).has("items")) it else null
            } catch (_: Exception) { null }
        }
    }

    if (jsonStr != null) {
        return try {
            val items = org.json.JSONObject(jsonStr).getJSONArray("items")
            val sb = StringBuilder()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val name = item.optString("item_name", "")
                val cat = item.optString("category", "")
                val tip = item.optString("disposal_tip", "")
                if (i > 0) sb.append("\n\n")
                sb.append("**$name**  →  $cat\n")
                if (tip.isNotEmpty()) sb.append("💡 $tip")
            }
            sb.toString() to jsonStr
        } catch (_: Exception) {
            answer to null
        }
    }
    return answer to null
}

/** 对话记录中的单条消息 */
data class ChatMessage(val role: String, val content: String) // role: "question" or "answer"

@Composable
fun AiModeScreen(
    onBack: () -> Unit,
    onNavigateToResult: (Bitmap, Long) -> Unit
) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }

    var isCameraActive by remember { mutableStateOf(true) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var transcript by remember { mutableStateOf("") }
    var chatHistory by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var currentPage by remember { mutableStateOf(0) }
    var isRecording by remember { mutableStateOf(false) }
    var isRecognizing by remember { mutableStateOf(false) }
    // 每个 answer 卡片独立播放控制: 记录当前正在播放的 answer 在 chatHistory 中的索引
    var playingAnswerIndex by remember { mutableStateOf(-1) }
    var pausedAnswerIndex by remember { mutableStateOf(-1) }
    var isTtsLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("准备就绪") }
    var currentRecordId by remember { mutableStateOf(-1L) }
    var conversationId by remember { mutableStateOf<String?>(null) }

    var audioRecord by remember { mutableStateOf<AudioRecord?>(null) }
    var audioFile = remember { File(context.cacheDir, "recording.wav") }
    var audioBuffer by remember { mutableStateOf<ByteArray?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    val scrollState = rememberScrollState()

    // 相机权限检查
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            Toast.makeText(context, "需要相机权限才能使用拍照功能", Toast.LENGTH_SHORT).show()
        }
    }

    // 检查相机权限（仅在首次进入时触发，避免重组时重复调用导致闪退）
    LaunchedEffect(Unit) {
        if (isCameraActive && !hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // 录音权限请求
    var hasRecordPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var shouldStartRecordingAfterPermission by remember { mutableStateOf(false) }

    // 释放 AudioRecord
    fun releaseRecorder() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    // 实际执行录音的函数
    fun performStartRecording() {
        if (isRecording) return
        
        try {
            if (audioFile.exists()) audioFile.delete()

            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(context, "录音初始化失败", Toast.LENGTH_SHORT).show()
                return
            }

            audioRecord = recorder
            val maxBytes = sampleRate * 2 * 10 // 16kHz * 16bit * 10秒 = 320000 bytes
            audioBuffer = ByteArray(maxBytes)
            isRecording = true
            statusMessage = "正在录音（最多10秒）..."

            scope.launch(Dispatchers.IO) {
                recorder.startRecording()
                var totalBytes = 0
                val buffer = ByteArray(bufferSize)
                
                while (isRecording && totalBytes < audioBuffer!!.size) {
                    val bytesRead = recorder.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        System.arraycopy(buffer, 0, audioBuffer!!, totalBytes, bytesRead)
                        totalBytes += bytesRead
                    } else if (bytesRead < 0) {
                        break // 录音已被停止
                    }
                }
                
                // stop() 可能已在 stopRecording() 中调用过，但多次调用没问题
                try { recorder.stop() } catch (_: Exception) {}
                try { recorder.release() } catch (_: Exception) {}
                audioRecord = null

                // 将 PCM 数据写入 WAV 文件
                if (totalBytes > 0) {
                    writeWavFile(audioFile, audioBuffer!!, totalBytes, sampleRate)
                    statusMessage = "录音完成"
                } else {
                    statusMessage = "录音失败"
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "录音启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 权限请求回调
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            if (shouldStartRecordingAfterPermission) {
                shouldStartRecordingAfterPermission = false
                performStartRecording()
            }
        } else {
            shouldStartRecordingAfterPermission = false
            Toast.makeText(context, "需要录音权限才能使用语音功能", Toast.LENGTH_SHORT).show()
        }
    }

    // 开始录音（带权限检查）
    fun startRecording() {
        if (isRecording) return
        
        // 检查录音权限
        if (!hasRecordPermission) {
            shouldStartRecordingAfterPermission = true
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        
        performStartRecording()
    }

    // 停止录音并识别
    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        // 立即停止录音，让 IO 协程中的 read() 返回 -1 从而退出循环
        audioRecord?.let { r ->
            try { if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) r.stop() } catch (_: Exception) {}
            // 不调用 release()，由 IO 协程负责释放
        }
        statusMessage = "正在识别语音..."

        scope.launch {
            // 等待 IO 协程完成释放并写入文件
            delay(300)
            val result = AiRecognitionClient.speechToText(audioFile)
            result.onSuccess { text ->
                transcript = text
                statusMessage = "语音输入: $text"
            }.onFailure { e ->
                transcript = "语音识别失败"
                statusMessage = "语音识别失败: ${e.message}"
                Toast.makeText(context, "语音识别失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 拍照
    val takePhoto: () -> Unit = {
        val imageCapture = imageCaptureRef.value
        if (imageCapture == null) {
            Toast.makeText(context, "相机初始化中…", Toast.LENGTH_SHORT).show()
        } else {
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val raw = ImageUtils.imageProxyToBitmap(image)
                        val rot = image.imageInfo.rotationDegrees
                        image.close()
                        val bitmap = ImageUtils.rotateBitmap(raw, rot)
                        capturedBitmap = bitmap
                        isCameraActive = false
                        // 不再在此处保存到数据库，移到了 startAiRecognition 中同步执行
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Toast.makeText(context, "拍照失败", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // 文字转语音并播放（指定回答索引以独立控制）
    fun playSpeech(text: String, answerIndex: Int = -1) {
        if (text.isEmpty() || isTtsLoading) return
        isTtsLoading = true
        mediaPlayer?.let { mp ->
            try { mp.release() } catch (_: Exception) {}
        }
        mediaPlayer = null
        playingAnswerIndex = answerIndex
        pausedAnswerIndex = -1
        scope.launch {
            val result = AiRecognitionClient.textToSpeech(text)
            withContext(Dispatchers.Main) {
                isTtsLoading = false
                result.onSuccess { audioBytes ->
                    try {
                        val tempFile = File(context.cacheDir, "tts_output.mp3")
                        tempFile.writeBytes(audioBytes)
                        val mp = MediaPlayer()
                        mp.setDataSource(tempFile.absolutePath)
                        mp.prepare()
                        mp.setOnCompletionListener {
                            mp.release()
                            if (mediaPlayer == mp) {
                                playingAnswerIndex = -1
                                pausedAnswerIndex = -1
                                mediaPlayer = null
                            }
                            tempFile.delete()
                        }
                        mp.start()
                        mediaPlayer = mp
                    } catch (e: Exception) {
                        mediaPlayer = null
                        playingAnswerIndex = -1
                        pausedAnswerIndex = -1
                        Toast.makeText(context, "语音播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { e ->
                    playingAnswerIndex = -1
                    pausedAnswerIndex = -1
                    Toast.makeText(context, "语音合成失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 暂停朗读
    fun pauseSpeech() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                pausedAnswerIndex = playingAnswerIndex
            }
        }
    }

    // 继续朗读
    fun resumeSpeech() {
        mediaPlayer?.let { mp ->
            if (!mp.isPlaying) {
                mp.start()
                pausedAnswerIndex = -1
            }
        }
    }

    // 停止朗读
    fun stopSpeech() {
        mediaPlayer?.let { mp ->
            try { mp.release() } catch (_: Exception) {}
        }
        mediaPlayer = null
        playingAnswerIndex = -1
        pausedAnswerIndex = -1
        isTtsLoading = false
    }

    // 开始 AI 识别（首次或追问）
    fun startAiRecognition() {
        if (isRecognizing) return
        val query = transcript.trim()
        if (query.isEmpty() && capturedBitmap == null) {
            Toast.makeText(context, "请先拍照或输入语音", Toast.LENGTH_SHORT).show()
            return
        }
        isRecognizing = true
        statusMessage = if (conversationId.isNullOrEmpty()) "正在分析..." else "正在追问..."

        scope.launch {
            try {
                val displayText: String
                val jsonStr: String?

                if (conversationId.isNullOrEmpty() && capturedBitmap != null) {
                    // 首次：上传图片 + 分析
                    val recordId = withContext(Dispatchers.IO) {
                        saveAndGetRecordId(context, db, capturedBitmap!!)
                    }
                    currentRecordId = recordId

                    val q = query.ifEmpty { "分析这张图片是什么垃圾，该怎么处理" }
                    val result = AiRecognitionClient.uploadAndChat(capturedBitmap!!, q, null)
                    withContext(Dispatchers.Main) {
                        result.onSuccess { chatResult ->
                            if (!chatResult.conversationId.isNullOrEmpty()) conversationId = chatResult.conversationId
                            val parsed = parseAiJsonResult(chatResult.text)
                            displayText = parsed.first
                            jsonStr = parsed.second

                            if (currentRecordId > 0 && jsonStr != null) {
                                withContext(Dispatchers.IO) {
                                    try { db.captureRecordDao().updateAiResult(currentRecordId, jsonStr) } catch (_: Exception) {}
                                }
                            }

                            chatHistory = listOf(ChatMessage("question", q), ChatMessage("answer", displayText))
                            currentPage = 0
                            transcript = ""
                            statusMessage = "分析完成"
                            playSpeech(displayText, answerIndex = 1)
                        }.onFailure { e ->
                            statusMessage = "分析失败: ${e.message}"
                            Toast.makeText(context, "分析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else if (!conversationId.isNullOrEmpty()) {
                    // 追问
                    if (query.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "请先通过语音输入问题", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                    val result = AiRecognitionClient.sendChat(query, conversationId!!)
                    withContext(Dispatchers.Main) {
                        result.onSuccess { chatResult ->
                            if (!chatResult.conversationId.isNullOrEmpty()) conversationId = chatResult.conversationId
                            val parsed = parseAiJsonResult(chatResult.text)
                            displayText = parsed.first
                            jsonStr = parsed.second

                            val newHistory = chatHistory + ChatMessage("question", query) + ChatMessage("answer", displayText)
                            val newAnswerIndex = newHistory.size - 1
                            chatHistory = newHistory
                            currentPage = (newHistory.size / 2) - 1
                            transcript = ""
                            statusMessage = "追问完成"
                            playSpeech(displayText, answerIndex = newAnswerIndex)
                        }.onFailure { e ->
                            statusMessage = "追问失败: ${e.message}"
                            Toast.makeText(context, "追问失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "请先拍照", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusMessage = "错误: ${e.message}"
                    Toast.makeText(context, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                withContext(Dispatchers.Main) { isRecognizing = false }
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isCameraActive) {
                CameraPreviewView(
                    modifier = Modifier.fillMaxSize(),
                    imageCaptureRef = imageCaptureRef,
                    hasCameraPermission = hasCameraPermission
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 48.dp, start = 8.dp)
                ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)
                        .size(72.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                        .clickable { takePhoto() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                        Text("AI 识别模式", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // 拍照预览
                    val bmp = capturedBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.FillWidth
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "重新拍照",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                isCameraActive = true
                                capturedBitmap = null
                                transcript = ""
                                chatHistory = emptyList()
                                conversationId = null
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    // 语音输入卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRecording)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(20.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 麦克风按钮
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRecording) Color(0xFFFF6B6B)
                                        else MaterialTheme.colorScheme.primary
                                    )
                                    .clickable {
                                        if (isRecording) stopRecording() else startRecording()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (isRecording) "🔴" else "🎤",
                                    fontSize = 24.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "语音输入",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (isRecording) "松开停止录音" else "点击开始录音",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 语音转文字结果
                    if (transcript.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "语音内容",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    transcript,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 识别/追问按钮
                    val buttonText = when {
                        isRecognizing -> ""
                        conversationId.isNullOrEmpty() -> "开始识别"
                        else -> "发送追问"
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(enabled = !isRecognizing) { startAiRecognition() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecognizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                buttonText,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        statusMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 页面切换指示器
                    val totalPages = (chatHistory.size + 1) / 2
                    if (totalPages > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (currentPage > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                    .clickable(enabled = currentPage > 0) { currentPage-- }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text("◀", fontSize = 16.sp) }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "第 ${currentPage + 1}/${totalPages} 轮",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (currentPage < totalPages - 1) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                    .clickable(enabled = currentPage < totalPages - 1) { currentPage++ }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) { Text("▶", fontSize = 16.sp) }
                        }
                    }

                    // 当前页的对话内容
                    if (chatHistory.isNotEmpty()) {
                        val startIdx = currentPage * 2
                        val endIdx = minOf(startIdx + 2, chatHistory.size)
                        for (i in startIdx until endIdx) {
                            val msg = chatHistory[i]
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.role == "question")
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                else
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (msg.role == "question") "🗣️ 你" else "🤖 AI",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (msg.role == "answer") {
                                        Spacer(modifier = Modifier.weight(1f))
                                        // 朗读按钮：点击后从头开始播放
                                        val isThisPlaying = playingAnswerIndex == i
                                        val isThisPaused = pausedAnswerIndex == i
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(if (isTtsLoading) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                .clickable(enabled = !isTtsLoading) { playSpeech(msg.content, answerIndex = i) }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(if (isTtsLoading) "⏳" else "🔊", fontSize = 14.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("朗读", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        if (isThisPlaying) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            // 暂停/继续切换按钮
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                    .clickable { if (isThisPaused) resumeSpeech() else pauseSpeech() }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(if (isThisPaused) "▶️" else "⏸️", fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        if (isThisPaused) "继续" else "暂停",
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                if (msg.role == "question") {
                                    Text(msg.content, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
                                } else {
                                    Text(renderMarkdown(msg.content), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
                                }
                            }
                        }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
