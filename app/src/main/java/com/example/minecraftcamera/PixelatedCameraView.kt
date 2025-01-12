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

        fun setDisplayRotation(rotation: Int) {
            displayRotation = rotation
            surfaceTexture?.let { texture ->
                when (rotation) {
                    0 -> texture.setDefaultBufferSize(surfaceWidth, surfaceHeight)
                    90, 270 -> texture.setDefaultBufferSize(surfaceHeight, surfaceWidth)
                    180 -> texture.setDefaultBufferSize(surfaceWidth, surfaceHeight)
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
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                
                surfaceTexture?.release()
                surfaceTexture = SurfaceTexture(textureId).apply {
                    setOnFrameAvailableListener { 
                        requestRender() 
                        Log.d(TAG, "New frame available")
                    }
                }

                try {
                    pixelShader = PixelShader()
                    isContextCreated = true
                    Log.d(TAG, "PixelShader initialized successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize PixelShader", e)
                }
                
                Log.d(TAG, "Surface created successfully with texture ID: $textureId")
            } catch (e: Exception) {
                Log.e(TAG, "Error in onSurfaceCreated", e)
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            Log.d(TAG, "Surface changed to $width x $height")
            surfaceWidth = width
            surfaceHeight = height
            GLES20.glViewport(0, 0, width, height)
            setDisplayRotation(displayRotation)
        }

        override fun onDrawFrame(gl: GL10?) {
            try {
                if (!isContextCreated) {
                    Log.e(TAG, "OpenGL context not created")
                    return
                }

                if (textureId <= 0) {
                    Log.e(TAG, "Invalid texture ID in onDrawFrame")
                    return
                }

                surfaceTexture?.updateTexImage()
                
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                
                var error = GLES20.glGetError()
                if (error != GLES20.GL_NO_ERROR) {
                    Log.e(TAG, "GL error before draw: ${getErrorString(error)}")
                    return
                }
                
                pixelShader?.let { shader ->
                    shader.draw(textureId, pixelSize, surfaceWidth.toFloat(), surfaceHeight.toFloat())
                } ?: run {
                    Log.e(TAG, "PixelShader not initialized")
                    // 尝试重新初始化
                    if (isContextCreated) {
                        try {
                            pixelShader = PixelShader()
                            Log.d(TAG, "PixelShader re-initialized")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to re-initialize PixelShader", e)
                        }
                    }
                }
                
                error = GLES20.glGetError()
                if (error != GLES20.GL_NO_ERROR) {
                    Log.e(TAG, "GL error after draw: ${getErrorString(error)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onDrawFrame", e)
            }
        }

        private fun getErrorString(error: Int): String {
            return when (error) {
                GLES20.GL_INVALID_ENUM -> "Invalid enum"
                GLES20.GL_INVALID_VALUE -> "Invalid value"
                GLES20.GL_INVALID_OPERATION -> "Invalid operation"
                GLES20.GL_OUT_OF_MEMORY -> "Out of memory"
                else -> "Error code: $error"
            }
        }
    }
} 