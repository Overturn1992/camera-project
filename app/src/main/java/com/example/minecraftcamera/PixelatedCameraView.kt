package com.example.minecraftcamera

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class PixelatedCameraView(context: Context) : GLSurfaceView(context) {
    private val renderer: PixelatedRenderer
    private var pixelSize: Float = 10f

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        preserveEGLContextOnPause = true
        renderer = PixelatedRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setPixelSize(size: Float) {
        pixelSize = size
        renderer.pixelSize = size
        requestRender()
    }

    fun getSurfaceTexture(): SurfaceTexture? = renderer.surfaceTexture

    private inner class PixelatedRenderer : Renderer {
        var pixelSize: Float = 10f
        var surfaceTexture: SurfaceTexture? = null
        private val pixelShader = PixelShader()
        private var textureId: Int = -1

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]
            
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            
            surfaceTexture = SurfaceTexture(textureId)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            surfaceTexture?.updateTexImage()
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            pixelShader.draw(textureId, pixelSize)
        }
    }
} 