package com.example.minecraftcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        android.util.Log.e("MinecraftCamera", "MainActivity onCreate called")
        
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, CAMERA_PERMISSION_REQUEST
            )
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { permission ->
        val granted = ContextCompat.checkSelfPermission(baseContext, permission) == PackageManager.PERMISSION_GRANTED
        Log.d("MainActivity", "Permission $permission granted: $granted")
        granted
    }

    private fun startCamera() {
        Log.d("MainActivity", "Starting camera")
        setContent {
            Log.d("MainActivity", "Setting content")
            CameraScreen()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能运行应用", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
} 