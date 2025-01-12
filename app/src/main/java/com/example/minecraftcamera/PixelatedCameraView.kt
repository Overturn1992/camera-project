package com.example.minecraftcamera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLES11Ext
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import java.nio.IntBuffer
import java.nio.ByteBuffer

class PixelatedCameraView(context: Context) : GLSurfaceView(context) {
    companion object {
        private const val TAG = "MinecraftCamera"
    }

    private val renderer: PixelatedRenderer
    private var pixelSize: Float = 10f
    private var displayRotation: Int = 0
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0
    private var photoCallback: ((Uri?) -> Unit)? = null

    init {
        Log.i(TAG, "Initializing PixelatedCameraView")
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        
        renderer = PixelatedRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        Log.i(TAG, "PixelatedCameraView initialization completed")
    }

    fun takePhoto(context: Context, callback: (Uri?) -> Unit) {
        photoCallback = callback
        queueEvent {
            try {
                // 创建帧缓冲区
                val fboId = IntArray(1)
                GLES20.glGenFramebuffers(1, fboId, 0)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0])

                // 创建纹理
                val textureId = IntArray(1)
                GLES20.glGenTextures(1, textureId, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0])

                val width = renderer.surfaceWidth
                val height = renderer.surfaceHeight

                // 设置纹理参数
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

                // 将纹理附加到帧缓冲区
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, textureId[0], 0)

                // 渲染一帧
                renderer.drawFrame()

                // 读取像素
                val buffer = ByteBuffer.allocateDirect(width * height * 4)
                GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)

                // 创建位图
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                buffer.position(0)
                bitmap.copyPixelsFromBuffer(buffer)

                // 根据屏幕方向旋转位图
                val matrix = android.graphics.Matrix()
                when (displayRotation) {
                    Surface.ROTATION_0 -> matrix.postRotate(0f)
                    Surface.ROTATION_90 -> matrix.postRotate(90f)
                    Surface.ROTATION_180 -> matrix.postRotate(180f)
                    Surface.ROTATION_270 -> matrix.postRotate(270f)
                }

                // 应用旋转
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
                bitmap.recycle()

                // 保存图片
                val filename = "Minecraft_${System.currentTimeMillis()}.jpg"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }
                }
                
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                    contentValues
                )
                
                uri?.let { 
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                    }
                }
                
                // 清理资源
                rotatedBitmap.recycle()
                GLES20.glDeleteTextures(1, textureId, 0)
                GLES20.glDeleteFramebuffers(1, fboId, 0)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

                // 在主线程中回调
                Handler(Looper.getMainLooper()).post {
                    photoCallback?.invoke(uri)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error taking photo", e)
                Handler(Looper.getMainLooper()).post {
                    photoCallback?.invoke(null)
                }
            }
        }
    }

    fun setDisplayRotation(rotation: Int) {
        displayRotation = rotation
        queueEvent {
            renderer.setDisplayRotation(rotation)
            requestRender()
        }
    }

    fun setPixelSize(size: Float) {
        queueEvent {
            renderer.pixelSize = size
            requestRender()
        }
    }

    fun getSurfaceTexture(): SurfaceTexture? = renderer.surfaceTexture

    fun setPreviewSize(width: Int, height: Int) {
        Log.i(TAG, "Setting preview size to ${width}x${height}")
        previewWidth = width
        previewHeight = height
        queueEvent {
            renderer.setPreviewSize(width, height)
            requestRender()
        }
    }

    private inner class PixelatedRenderer : Renderer {
        var pixelSize: Float = 10f
        var surfaceTexture: SurfaceTexture? = null
        private var pixelShader: PixelShader? = null
        private var textureId: Int = -1
        var surfaceWidth: Int = 0
        var surfaceHeight: Int = 0
        private var previewWidth: Int = 0
        private var previewHeight: Int = 0
        private var isContextCreated = false
        private var displayRotation: Int = 0
        private val transformMatrix = FloatArray(16)

        fun setDisplayRotation(rotation: Int) {
            displayRotation = rotation
            updateTextureMatrix()
        }

        fun setPreviewSize(width: Int, height: Int) {
            previewWidth = width
            previewHeight = height
            updateTextureMatrix()
        }

        private fun updateTextureMatrix() {
            surfaceTexture?.let { texture ->
                // 获取相机的变换矩阵
                texture.getTransformMatrix(transformMatrix)
                
                // 设置相机预览缓冲区大小为实际的预览尺寸
                if (previewWidth > 0 && previewHeight > 0) {
                    texture.setDefaultBufferSize(previewWidth, previewHeight)
                    Log.d(TAG, "Set buffer size to ${previewWidth}x${previewHeight}")
                }
            }
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            try {
                Log.i(TAG, "onSurfaceCreated started")
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                
                if (textureId > 0) {
                    val textures = IntArray(1) { textureId }
                    GLES20.glDeleteTextures(1, textures, 0)
                }
                
                val textures = IntArray(1)
                GLES20.glGenTextures(1, textures, 0)
                textureId = textures[0]
                
                if (textureId == 0) {
                    Log.e(TAG, "Failed to generate texture")
                    return
                }
                
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                
                surfaceTexture?.release()
                surfaceTexture = SurfaceTexture(textureId).apply {
                    setOnFrameAvailableListener { 
                        requestRender() 
                    }
                }

                try {
                    pixelShader = PixelShader()
                    isContextCreated = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize PixelShader", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onSurfaceCreated", e)
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            Log.d(TAG, "Surface changed to $width x $height")
            surfaceWidth = width
            surfaceHeight = height
            GLES20.glViewport(0, 0, width, height)
            
            // 更新变换矩阵和缓冲区大小
            updateTextureMatrix()
        }

        override fun onDrawFrame(gl: GL10?) {
            try {
                if (!isContextCreated || textureId <= 0) {
                    Log.w(TAG, "Context not created or invalid texture id")
                    return
                }

                surfaceTexture?.let { texture ->
                    texture.updateTexImage()
                    updateTextureMatrix()
                    
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                    
                    // 使用实际的预览尺寸而不是表面尺寸
                    val width = if (previewWidth > 0) previewWidth.toFloat() else surfaceWidth.toFloat()
                    val height = if (previewHeight > 0) previewHeight.toFloat() else surfaceHeight.toFloat()
                    
                    pixelShader?.draw(
                        textureId,
                        pixelSize,
                        width,
                        height,
                        transformMatrix
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onDrawFrame", e)
            }
        }

        fun drawFrame() {
            onDrawFrame(null)
        }
    }
} 