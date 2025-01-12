package com.example.minecraftcamera

import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture.OutputFileOptions
import android.view.Surface
import android.view.WindowManager
import android.view.OrientationEventListener
import android.view.Display
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CameraScreen() {
    val TAG = "MinecraftCamera"
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val executor = ContextCompat.getMainExecutor(context)
    
    // 监听设备方向变化
    var deviceRotation by remember { mutableStateOf(0) }
    val orientationEventListener = remember {
        object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                
                // 将传感器方向转换为Surface旋转值
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270    // 逆时针旋转90度
                    in 135..224 -> Surface.ROTATION_180   // 旋转180度
                    in 225..314 -> Surface.ROTATION_90    // 顺时针旋转90度
                    else -> Surface.ROTATION_0            // 正常竖屏
                }
                
                if (rotation != deviceRotation) {
                    deviceRotation = rotation
                    imageCapture?.targetRotation = rotation
                }
            }
        }
    }

    DisposableEffect(Unit) {
        orientationEventListener.enable()
        onDispose {
            orientationEventListener.disable()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        var pixelatedPreview by remember { mutableStateOf<PixelatedCameraView?>(null) }
        var pixelSize by remember { mutableStateOf(8f) }
        
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PixelatedCameraView(context).apply {
                    setPixelSize(pixelSize)
                    pixelatedPreview = this
                }
            }
        ) { view ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder()
                    .setTargetRotation(deviceRotation)
                    .build()
                    .also {
                        it.setSurfaceProvider { request ->
                            val surfaceTexture = pixelatedPreview?.getSurfaceTexture()
                            if (surfaceTexture != null) {
                                surfaceTexture.setDefaultBufferSize(
                                    request.resolution.width,
                                    request.resolution.height
                                )
                                pixelatedPreview?.setPreviewSize(
                                    request.resolution.width,
                                    request.resolution.height
                                )
                                val surface = Surface(surfaceTexture)
                                request.provideSurface(surface, executor) { }
                            }
                        }
                    }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(deviceRotation)
                    .setJpegQuality(100)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                } catch(e: Exception) {
                    Log.e(TAG, "Use case binding failed", e)
                }
            }, executor)
        }

        // 添加拍照按钮和像素大小滑块
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            // 像素大小滑块
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "像素大小",
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = pixelSize,
                    onValueChange = { 
                        pixelSize = it
                        pixelatedPreview?.setPixelSize(it)
                    },
                    valueRange = 2f..20f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
                Text(
                    text = String.format("%.0f", pixelSize),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 拍照按钮
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp)
            ) {
                // 外圈白色圆形背景
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.8f),
                    shadowElevation = 4.dp
                ) {
                    // 内圈蓝色圆形按钮
                    Surface(
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = {
                            takePhoto(
                                imageCapture = imageCapture,
                                context = context,
                                executor = executor
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "拍照",
                            modifier = Modifier
                                .size(40.dp)
                                .padding(8.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun takePhoto(
    imageCapture: ImageCapture?,
    context: android.content.Context,
    executor: Executor
) {
    imageCapture?.let {
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.CHINA)
            .format(System.currentTimeMillis())
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MinecraftCamera")
            }
        }

        val outputOptions = OutputFileOptions
            .Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Toast.makeText(
                        context,
                        "照片已保存",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(
                        context,
                        "保存失败: ${exc.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e("MinecraftCamera", "Photo capture failed: ${exc.message}", exc)
                }
            }
        )
    }
} 