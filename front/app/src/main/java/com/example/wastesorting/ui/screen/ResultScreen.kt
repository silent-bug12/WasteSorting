package com.example.wastesorting.ui.screen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wastesorting.data.GarbageClassifier
import com.example.wastesorting.data.db.GarbageDatabase
import com.example.wastesorting.util.catColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 获取各分类的简单投放建议
 */
fun getDisposalTip(classId: Int): String = when (classId) {
    // 其他垃圾 (0-5)
    0 -> "沥干食物残渣后投入其他垃圾桶"
    1 -> "已污染的塑料不可回收，投入其他垃圾桶"
    2 -> "确保烟蒂完全熄灭后投放"
    3 -> "直接投入其他垃圾桶即可"
    4 -> "用纸包裹碎片，避免扎伤清洁人员"
    5 -> "直接投入其他垃圾桶"
    // 厨余垃圾 (6-13)
    6 -> "沥干水分后投入厨余垃圾桶"
    7 -> "敲碎或掰小后再投放，便于处理"
    8 -> "直接投入厨余垃圾桶"
    9 -> "直接投入厨余垃圾桶"
    10 -> "沥干后投放，建议用滤网收集"
    11 -> "直接投入厨余垃圾桶"
    12 -> "直接投入厨余垃圾桶"
    13 -> "直接投入厨余垃圾桶"
    // 可回收物 (14-36)
    14 -> "建议投放至专门回收点，勿混入其他垃圾"
    15 -> "清空内部物品后投入可回收物桶"
    16 -> "清洗干净、晾干后投放"
    17 -> "直接投入可回收物桶"
    18 -> "冲洗干净后投放效果更佳"
    19 -> "直接投入可回收物桶"
    20 -> "撕掉胶带和面单后折叠投放"
    21 -> "可直接投入可回收物桶"
    22 -> "建议投入社区旧衣回收箱"
    23 -> "压扁后投放，节省空间"
    24 -> "拆解后，布料和填充物分别投放"
    25 -> "直接投入可回收物桶"
    26 -> "冲洗干净、拧紧瓶盖后投放"
    27 -> "用纸包好，防止破碎伤人"
    28 -> "直接投入可回收物桶"
    29 -> "直接投入可回收物桶"
    30 -> "压平折叠后投放，减少体积"
    31 -> "倒空内容物、冲洗后投放"
    32 -> "冲洗晾干后投放"
    33 -> "清洗后压扁，减少占用空间"
    34 -> "直接投入可回收物桶"
    35 -> "倒空余油后投入可回收物桶"
    36 -> "压扁后拧紧瓶盖投放"
    // 有害垃圾 (37-39)
    37 -> "投入有害垃圾桶，切勿混入其他垃圾"
    38 -> "挤空内容物后投入有害垃圾桶"
    39 -> "整盒投入有害垃圾桶，勿拆包装"
    else -> "请根据当地分类指南投放"
}

@Composable
fun ResultScreen(bitmap: Bitmap, recordId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { GarbageDatabase.getInstance(context) }
    val classifier = remember { GarbageClassifier.getInstance(context) }
    var result by remember { mutableStateOf<GarbageClassifier.PredictionResult?>(null) }
    var isRecognizing by remember { mutableStateOf(true) }

    LaunchedEffect(recordId) {
        val r = withContext(Dispatchers.IO) { classifier.classify(bitmap) }
        db.captureRecordDao().updateRecognition(recordId, r.label, r.category, r.confidence)
        result = r; isRecognizing = false
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("识别结果", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            Spacer(modifier = Modifier.height(16.dp))

            Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth
            )
            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("识别结果", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (isRecognizing) {
                        Text("正在识别…", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val r = result
                        if (r != null && r.classId >= 0) {
                            Text(r.label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(catColor(r.category)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                    Text(r.category, color = Color.White, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("置信度 ${"%.1f".format(r.confidence * 100)}%", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            // 投放建议
                            Box(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(catColor(r.category).copy(alpha = 0.1f))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Text("💡", fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = getDisposalTip(r.classId),
                                        fontSize = 14.sp,
                                        color = catColor(r.category),
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        } else {
                            Text("识别失败", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
