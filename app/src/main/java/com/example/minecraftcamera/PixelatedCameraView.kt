package com.example.minecraftcamera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLES11Ext
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PixelatedCameraView(context: Context) : GLSurfaceView(context) {
    companion object {
        private const val TAG = "MinecraftCamera"
    }

    private val renderer: PixelatedRenderer
    private var pixelSize: Float = 10f
    private var displayRotation: Int = 0

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

    private inner class PixelatedRenderer : Renderer {
        var pixelSize: Float = 10f
        var surfaceTexture: SurfaceTexture? = null
        private var pixelShader: PixelShader? = null
        private var textureId: Int = -1
        private var surfaceWidth: Int = 0
        private var surfaceHeight: Int = 0
        private var isContextCreated = false
        private var displayRotation: Int = 0
        private val transformMatrix = FloatArray(16)

        fun setDisplayRotation(rotation: Int) {
            displayRotation = rotation
            updateTextureMatrix()
        }

        private fun updateTextureMatrix() {
            surfaceTexture?.let { texture ->
                // 获取相机的变换矩阵
                texture.getTransformMatrix(transformMatrix)
                
                // 根据显示方向调整缓冲区大小
                when (displayRotation) {
                    0, 180 -> texture.setDefaultBufferSize(surfaceWidth, surfaceHeight)
                    90, 270 -> texture.setDefaultBufferSize(surfaceHeight, surfaceWidth)
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
                    
                    pixelShader?.draw(
                        textureId,
                        pixelSize,
                        surfaceWidth.toFloat(),
                        surfaceHeight.toFloat(),
                        transformMatrix
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onDrawFrame", e)
            }
        }
    }
} 