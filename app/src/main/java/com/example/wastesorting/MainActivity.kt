package com.example.wastesorting

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageCapturedCallback
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.wastesorting.data.api.GarbageRecognitionClient
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.data.db.entity.CaptureRecordEntity
import com.example.wastesorting.ui.theme.WasteSortingTheme
import com.example.wastesorting.util.ImageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "WasteSorting"

// ============================================================
// 页面导航
// ============================================================
sealed class Screen {
    object Main : Screen()
    object BrowseRecords : Screen()
}

// ============================================================
// MainActivity
// ============================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WasteSortingTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

    when (currentScreen) {
        is Screen.Main -> WasteSortingScreen(
            onNavigateToRecords = { currentScreen = Screen.BrowseRecords }
        )
        is Screen.BrowseRecords -> BrowseRecordsScreen(
            onBack = { currentScreen = Screen.Main }
        )
    }
}

// ============================================================
// 主界面
// ============================================================
@Composable
fun WasteSortingScreen(onNavigateToRecords: () -> Unit = {}) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var isCameraActive by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    // 定格画面（拍照后一直定格，识别成功或点击画面后解锁）
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // 最近一次拍照的 Bitmap（识别用）
    var lastCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // 最近一次拍照的数据库记录 ID
    var lastCaptureId by remember { mutableStateOf<Long?>(null) }

    // 解锁定格画面
    val unlockFreeze: () -> Unit = {
        frozenBitmap = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPermission = grants[Manifest.permission.CAMERA] == true
        if (hasCameraPermission) {
            isCameraActive = true
        } else {
            Toast.makeText(context, "需要相机权限才能使用摄像头", Toast.LENGTH_SHORT).show()
        }
    }

    val toggleCamera: () -> Unit = {
        if (isCameraActive) {
            isCameraActive = false
            imageCaptureRef.value = null
        } else {
            if (hasCameraPermission) {
                isCameraActive = true
            } else {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
        }
    }

    // 拍照：定格画面，静默保存
    val takePhoto: () -> Unit = {
        if (!isCameraActive) {
            Toast.makeText(context, "请先开启摄像头", Toast.LENGTH_SHORT).show()
        } else {
            val imageCapture = imageCaptureRef.value
            if (imageCapture == null) {
                Toast.makeText(context, "相机正在初始化…", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = ImageUtils.imageProxyToBitmap(image)
                                image.close()

                                frozenBitmap = bitmap
                                lastCapturedBitmap = bitmap

                                // 后台保存到 MediaStore 并写入数据库
                                scope.launch(Dispatchers.IO) {
                                    saveAndRecord(context, db, bitmap) { recordId ->
                                        lastCaptureId = recordId
                                    }
                                }
                            }

                            override fun onError(exc: ImageCaptureException) {
                                Log.e(TAG, "拍照失败: ${exc.message}", exc)
                                Toast.makeText(context, "拍照失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "拍照出错: ${e.message}", e)
                    Toast.makeText(context, "拍照出错", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 识别：调用 API → 写入数据库 → 提示结果
    val recognize: () -> Unit = {
        val id = lastCaptureId
        val bitmap = lastCapturedBitmap
        if (id == null || bitmap == null) {
            Toast.makeText(context, "请先拍照", Toast.LENGTH_SHORT).show()
        } else {
            scope.launch {
                Toast.makeText(context, "正在识别…", Toast.LENGTH_SHORT).show()
                val jpegBytes = GarbageRecognitionClient.bitmapToJpegBytes(bitmap)
                val result = GarbageRecognitionClient.recognize(jpegBytes)

                withContext(Dispatchers.Main) {
                    if (result.success && result.itemName != null && result.category != null) {
                        val confidence = result.confidence ?: 0f
                        db.captureRecordDao().updateRecognition(
                            id, result.itemName, result.category, confidence
                        )
                        Toast.makeText(
                            context,
                            "识别结果：${result.itemName}（${result.category}）",
                            Toast.LENGTH_LONG
                        ).show()
                        lastCaptureId = null
                        lastCapturedBitmap = null
                        unlockFreeze()
                    } else {
                        Toast.makeText(
                            context,
                            result.error ?: "识别失败，请重试",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 背景层
        if (isCameraActive && hasCameraPermission) {
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                imageCaptureRef = imageCaptureRef
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1B5E20),
                                Color(0xFF0D3010),
                                Color(0xFF000000)
                            )
                        )
                    )
            )
        }

        // 定格画面层（点击可解锁）
        if (frozenBitmap != null) {
            Image(
                bitmap = frozenBitmap!!.asImageBitmap(),
                contentDescription = "定格画面（点击解锁）",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { unlockFreeze() },
                contentScale = ContentScale.FillWidth
            )
        }

        // 预览区域遮罩（摄像头关闭时）
        if (!isCameraActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.5f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .clickable { toggleCamera() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "点击开启摄像头",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 16.sp
                )
            }
        }

        // 全屏透明层（摄像头开启时可点击关闭）
        if (isCameraActive && frozenBitmap == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { toggleCamera() }
            )
        }

        // 顶部：摄像头图标
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "摄像头",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp)
                .size(56.dp)
                .clickable { toggleCamera() },
            tint = if (isCameraActive) Color.White else Color.White.copy(alpha = 0.85f)
        )

        // 右上角：设置按钮
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "设置",
                modifier = Modifier
                    .size(32.dp)
                    .clickable { showSettingsMenu = true },
                tint = Color.White.copy(alpha = 0.85f)
            )
            DropdownMenu(
                expanded = showSettingsMenu,
                onDismissRequest = { showSettingsMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("浏览记录") },
                    onClick = {
                        showSettingsMenu = false
                        onNavigateToRecords()
                    }
                )
            }
        }

        // 底部按钮
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 36.dp, end = 36.dp, bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = recognize,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCameraActive)
                        Color.Black.copy(alpha = 0.4f)
                    else
                        Color.White.copy(alpha = 0.2f)
                )
            ) {
                Text("识别", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(4.dp, Color.White, CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable { takePhoto() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

// ============================================================
// 浏览记录页面（数据来自 Room 数据库）
// ============================================================
@Composable
fun BrowseRecordsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val records by db.captureRecordDao().getAllFlow().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B5E20),
                        Color(0xFF0D3010),
                        Color(0xFF000000)
                    )
                )
            )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp)
                .size(32.dp)
                .clickable { onBack() },
            tint = Color.White.copy(alpha = 0.85f)
        )

        Text(
            text = "浏览记录",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        if (records.isEmpty()) {
            Text(
                text = "暂无拍照记录",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 16.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 100.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (record.isRecognized) {
                                Text(
                                    text = "${record.itemName}  →  ${record.categoryName}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (record.confidence != null) {
                                    Text(
                                        text = "置信度: ${"%.1f".format(record.confidence * 100)}%",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "未识别",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 16.sp
                                )
                            }
                            Text(
                                text = dateFormat.format(Date(record.timestamp)),
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

// ============================================================
// CameraX 预览
// ============================================================
@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    imageCaptureRef: androidx.compose.runtime.MutableState<ImageCapture?>
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                imageCaptureRef.value = imageCapture
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e(TAG, "CameraX 绑定失败: ${e.message}", e)
                imageCaptureRef.value = null
                Toast.makeText(context, "摄像头启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            imageCaptureRef.value = null
            val f = ProcessCameraProvider.getInstance(context)
            f.addListener({
                try { f.get().unbindAll() } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(context))
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

// ============================================================
// 静默保存 Bitmap 到 MediaStore 并写入数据库
// ============================================================
private suspend fun saveAndRecord(
    context: android.content.Context,
    db: GarbageDatabase,
    bitmap: Bitmap,
    onSaved: (Long) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val name = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_$name.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WasteSorting")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
        )
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }
        }

        val uriStr = uri?.toString() ?: ""
        val record = CaptureRecordEntity(
            imageUri = uriStr,
            itemName = null,
            categoryName = null,
            confidence = null,
            isRecognized = false,
            timestamp = System.currentTimeMillis()
        )
        val recordId = db.captureRecordDao().insert(record)
        withContext(Dispatchers.Main) { onSaved(recordId) }
        Log.d(TAG, "拍照已保存: $uriStr, recordId=$recordId")
    } catch (e: Exception) {
        Log.e(TAG, "保存照片失败: ${e.message}", e)
    }
}
