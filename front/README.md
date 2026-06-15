# 垃圾分类智能识别 App (WasteSorting)

## 项目概述

基于 Android 的智能垃圾分类应用。用户可通过摄像头拍照，调用训练好的深度学习模型识别垃圾类别，并将识别记录持久化到本地数据库。

## 技术栈

| 技术 | 说明 |
|------|------|
| 语言 | Kotlin 100% |
| UI 框架 | Jetpack Compose + Material3 |
| 构建工具 | Gradle 8.13 + KSP |
| 最低 SDK | 24 (Android 7.0) |
| 目标 SDK | 36 (Android 14) |
| 数据库 | Room 2.6.1 (SQLite) |
| 相机 | CameraX 1.1.0-beta01 |
| 网络 | OkHttp 4.12 |
| JSON | org.json (Android 内置) |

## 项目结构

```
app/src/main/java/com/example/wastesorting/
├── MainActivity.kt              # 主界面 + 浏览记录页面 + CameraX 预览
├── data/
│   ├── db/
│   │   ├── GarbageDatabase.kt   # Room 数据库（单例 + 预填充）
│   │   ├── entity/Entities.kt   # 实体：Category / Item / Record
│   │   └── dao/Daos.kt          # DAO 接口
│   └── api/
│       ├── RecognitionResult.kt         # 识别结果数据类 + API 规范
│       └── GarbageRecognitionClient.kt  # OkHttp 客户端
├── util/
│   └── ImageUtils.kt            # ImageProxy → Bitmap 转换工具
└── ui/theme/                    # Compose 主题（颜色/排版）
```

## 数据库设计

### garbage_categories（垃圾类别）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER (PK) | 1=可回收物 2=厨余垃圾 3=有害垃圾 4=其他垃圾 |
| name | TEXT | 类别名称 |

### garbage_items（垃圾物品字典）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER (PK, 自增) | |
| name | TEXT | 物品名称（如"一次性快餐盒"） |
| category_id | INTEGER (FK) | 对应 garbage_categories.id |

> 首次启动时自动从 `res/raw/garbage_dict.json` 预填充 40 条分类数据。

### capture_records（拍照识别记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | INTEGER (PK, 自增) | |
| image_uri | TEXT | 照片 MediaStore URI |
| item_name | TEXT (可空) | 识别出的物品名 |
| category_name | TEXT (可空) | 识别出的类别 |
| confidence | REAL (可空) | 识别置信度 0~1 |
| is_recognized | INTEGER | 0=未识别 1=已识别 |
| timestamp | INTEGER | 拍照时间（Unix 毫秒） |

## API 接口规范

### 垃圾识别接口

App 通过 HTTP 调用部署好的模型服务。

```
POST /api/recognize
Content-Type: multipart/form-data
```

**请求参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| image | File | JPEG 图片（表单字段名 `image`） |

**成功响应（200）：**

```json
{
  "success": true,
  "item_name": "一次性快餐盒",
  "category": "其他垃圾",
  "confidence": 0.95
}
```

**失败响应：**

```json
{
  "success": false,
  "error": "无法识别图片中的物品"
}
```

### 配置服务地址

在 `GarbageRecognitionClient.kt` 中修改 `baseUrl`：

```kotlin
// 模拟器访问本机
GarbageRecognitionClient.baseUrl = "http://10.0.2.2:5000"

// 真机访问局域网服务器
GarbageRecognitionClient.baseUrl = "http://192.168.1.100:5000"
```

## 构建与运行

### 环境要求

- Android Studio Koala 或更新版本
- JDK 17+
- Android SDK 36

### 构建步骤

```bash
# 克隆/解压项目后
cd WasteSorting

# 编译
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

### 权限说明

| 权限 | 用途 |
|------|------|
| CAMERA | 拍照 |
| RECORD_AUDIO | 预留（后续视频录制） |

## 垃圾分类字典

字典文件：`app/src/main/res/raw/garbage_dict.json`

格式：`{"序号": "类别/物品名"}`

| 类别 | 数量 | 示例 |
|------|:--:|------|
| 可回收物 | 23 | 充电宝、旧衣服、饮料瓶、易拉罐、纸板箱… |
| 厨余垃圾 | 8 | 剩饭剩菜、水果果皮、茶叶渣、鱼骨、蛋壳… |
| 有害垃圾 | 3 | 干电池、软膏、过期药物 |
| 其他垃圾 | 6 | 一次性快餐盒、烟蒂、牙签、污损塑料… |

## 核心交互流程

```
[开启摄像头] → [拍照] → 画面定格 1.5s → [识别] → API 识别 → 写入数据库
                                                              ↓
[设置] → [浏览记录] ←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←←← 从数据库读取
```

## 后续扩展

1. **模型服务部署**：参照 API 规范实现 Flask/FastAPI 后端
2. **Room 升级**：capture_records 可关联 garbage_items 外键
3. **图片预览**：浏览记录中展示缩略图
4. **批量导出**：识别记录导出为 CSV/JSON
