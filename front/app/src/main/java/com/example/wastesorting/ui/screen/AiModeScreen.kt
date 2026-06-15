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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.wastesorting.R
import com.example.wastesorting.data.api.AiRecognitionClient
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.util.ImageUtils
import com.example.wastesorting.util.saveAndGetRecordId
import kotlinx.coroutines.Dispatchers
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
    var aiResult by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var isRecognizing by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("准备就绪") }

    var audioRecord by remember { mutableStateOf<AudioRecord?>(null) }
    var audioFile = remember { File(context.cacheDir, "recording.wav") }
    var audioBuffer by remember { mutableStateOf<ByteArray?>(null) }

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

    // 检查相机权限
    if (isCameraActive && !hasCameraPermission) {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
            audioBuffer = ByteArray(bufferSize * 10) // 约10秒的缓冲区
            isRecording = true
            statusMessage = "正在录音（请按住说话3-5秒）..."

            scope.launch(Dispatchers.IO) {
                recorder.startRecording()
                var totalBytes = 0
                val buffer = ByteArray(bufferSize)
                
                while (isRecording && totalBytes < audioBuffer!!.size) {
                    val bytesRead = recorder.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        System.arraycopy(buffer, 0, audioBuffer!!, totalBytes, bytesRead)
                        totalBytes += bytesRead
                    }
                }
                
                recorder.stop()
                recorder.release()
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
        releaseRecorder()
        isRecording = false
        statusMessage = "正在识别语音..."

        scope.launch {
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
                        scope.launch(Dispatchers.IO) { saveAndGetRecordId(context, db, bitmap) }
                    }

                    override fun onError(exc: ImageCaptureException) {
                        Toast.makeText(context, "拍照失败", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    // 文字转语音并播放
    fun playSpeech(text: String) {
        if (text.isEmpty()) return
        isSpeaking = true
        scope.launch {
            val result = AiRecognitionClient.textToSpeech(text)
            withContext(Dispatchers.Main) {
                isSpeaking = false
                result.onSuccess { audioBytes ->
                    try {
                        val tempFile = File(context.cacheDir, "tts_output.mp3")
                        tempFile.writeBytes(audioBytes)
                        val mediaPlayer = MediaPlayer()
                        mediaPlayer.setDataSource(tempFile.absolutePath)
                        mediaPlayer.prepare()
                        mediaPlayer.setOnCompletionListener {
                            it.release()
                            tempFile.delete()
                        }
                        mediaPlayer.start()
                    } catch (e: Exception) {
                        Toast.makeText(context, "语音播放失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { e ->
                    Toast.makeText(context, "语音合成失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 开始 AI 识别
    fun startAiRecognition() {
        val bitmap = capturedBitmap
        if (bitmap == null) {
            Toast.makeText(context, "请先拍照", Toast.LENGTH_SHORT).show()
            return
        }
        isRecognizing = true
        aiResult = ""
        statusMessage = "正在上传图片并分析..."

        scope.launch {
            val query = transcript.ifEmpty { "分析这张图片是什么垃圾，该怎么处理" }
            val result = AiRecognitionClient.uploadAndChat(bitmap, query)
            withContext(Dispatchers.Main) {
                isRecognizing = false
                result.onSuccess { answer ->
                    aiResult = answer
                    statusMessage = "分析完成"
                    // 自动朗读
                    playSpeech(answer)
                }.onFailure { e ->
                    aiResult = ""
                    statusMessage = "分析失败: ${e.message}"
                    Toast.makeText(context, "分析失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
                                aiResult = ""
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

                    // 开始识别按钮
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
                                "开始识别",
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

                    // AI 建议结果
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "🤖 AI 建议",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (aiResult.isNotEmpty()) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .clickable { playSpeech(aiResult) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("🔊", fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                if (isSpeaking) "朗读中..." else "朗读",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            if (aiResult.isEmpty()) {
                                Text(
                                    "智能投放建议将在此展示",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            } else {
                                Text(
                                    aiResult,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}
