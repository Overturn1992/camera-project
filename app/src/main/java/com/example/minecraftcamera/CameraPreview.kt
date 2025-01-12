package com.example.minecraftcamera

import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            }
        ) { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder()
                    .setTargetRotation(deviceRotation)
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
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

        IconButton(
            onClick = {
                takePhoto(
                    imageCapture = imageCapture,
                    context = context,
                    executor = executor
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(72.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = "Take photo",
                modifier = Modifier.size(48.dp)
            )
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