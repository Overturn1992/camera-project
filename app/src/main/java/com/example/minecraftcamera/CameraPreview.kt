package com.example.minecraftcamera

import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun CameraScreen() {
    val TAG = "MinecraftCamera"
    
    var pixelSize by remember { mutableStateOf(10f) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val pixelatedView = remember { PixelatedCameraView(context) }
    
    LaunchedEffect(Unit) {
        Log.i(TAG, "CameraScreen composable initialized")
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { 
                Log.i(TAG, "Creating PixelatedCameraView")
                pixelatedView 
            }
        ) { view ->
            Log.i(TAG, "Setting up camera preview")
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    Log.i(TAG, "Camera provider obtained")
                    
                    val preview = Preview.Builder()
                        .setTargetRotation(view.display.rotation)
                        .build()
                        
                    preview.setSurfaceProvider { request ->
                        Log.i(TAG, "Surface provider called with resolution: ${request.resolution}")
                        val texture = view.getSurfaceTexture()
                        if (texture == null) {
                            Log.e(TAG, "Failed to get surface texture")
                            return@setSurfaceProvider
                        }
                        
                        Log.i(TAG, "Got surface texture successfully")
                        view.setPreviewSize(request.resolution.width, request.resolution.height)
                        
                        texture.setDefaultBufferSize(
                            request.resolution.width,
                            request.resolution.height
                        )
                        
                        request.provideSurface(
                            Surface(texture),
                            ContextCompat.getMainExecutor(context)
                        ) { 
                            Log.i(TAG, "Surface provided to camera successfully")
                        }
                    }
                    
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    
                    try {
                        Log.i(TAG, "Unbinding previous use cases")
                        cameraProvider.unbindAll()
                        Log.i(TAG, "Binding new use cases")
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview
                        )
                        Log.i(TAG, "Camera use cases bound successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to bind camera use cases", e)
                        e.printStackTrace()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get camera provider", e)
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }

        DisposableEffect(pixelSize) {
            pixelatedView.setPixelSize(pixelSize)
            onDispose { }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            IconButton(
                onClick = { 
                    pixelatedView.takePhoto(context) { uri ->
                        // 拍照成功后的回调
                        Toast.makeText(context, "照片已保存", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoCamera,
                    contentDescription = "Take photo",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "像素化程度: ${pixelSize.toInt()}",
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Slider(
                    value = pixelSize,
                    onValueChange = { pixelSize = it },
                    valueRange = 1f..50f,
                    steps = 48,
                    modifier = Modifier.width(200.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
} 