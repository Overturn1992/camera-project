package com.example.minecraftcamera

import android.opengl.GLES20
import android.opengl.GLES11Ext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import android.util.Log

class CaptureShader {
    private var program: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureHandle: Int = 0
    private var transformMatrixHandle: Int = 0
    
    private val vertexShaderCode = """
        attribute vec4 position;
        attribute vec2 texCoord;
        varying vec2 texCoordVarying;
        uniform mat4 transformMatrix;
        
        void main() {
            gl_Position = position;
            texCoordVarying = texCoord;
        }
    """

    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        varying vec2 texCoordVarying;
        uniform samplerExternalOES texture;
        uniform mat4 transformMatrix;
        
        void main() {
            vec2 transformedCoord = (transformMatrix * vec4(texCoordVarying, 0.0, 1.0)).xy;
            gl_FragColor = texture2D(texture, transformedCoord);
        }
    """

    private val vertices = floatArrayOf(
        -1.0f, -1.0f,  // 左下
        1.0f, -1.0f,   // 右下
        -1.0f, 1.0f,   // 左上
        1.0f, 1.0f     // 右上
    )

    private val texCoords = floatArrayOf(
        0.0f, 0.0f,    // 左下
        1.0f, 0.0f,    // 右下
        0.0f, 1.0f,    // 左上
        1.0f, 1.0f     // 右上
    )

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer

    init {
        try {
            setupBuffers()
            setupShaders()
        } catch (e: Exception) {
            Log.e("CaptureShader", "Error during initialization", e)
            throw RuntimeException("Failed to initialize CaptureShader", e)
        }
    }

    private fun setupBuffers() {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer.position(0)
    }

    private fun setupShaders() {
        try {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

            program = GLES20.glCreateProgram()
            if (program == 0) {
                throw RuntimeException("Failed to create program")
            }

            GLES20.glAttachShader(program, vertexShader)
            checkGlError("Attach vertex shader")
            GLES20.glAttachShader(program, fragmentShader)
            checkGlError("Attach fragment shader")
            GLES20.glLinkProgram(program)
            checkGlError("Link program")

            val linked = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
            if (linked[0] == 0) {
                val error = GLES20.glGetProgramInfoLog(program)
                Log.e("CaptureShader", "Program linking error: $error")
                GLES20.glDeleteProgram(program)
                throw RuntimeException("Program linking failed: $error")
            }

            positionHandle = GLES20.glGetAttribLocation(program, "position")
            texCoordHandle = GLES20.glGetAttribLocation(program, "texCoord")
            textureHandle = GLES20.glGetUniformLocation(program, "texture")
            transformMatrixHandle = GLES20.glGetUniformLocation(program, "transformMatrix")

            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
        } catch (e: Exception) {
            Log.e("CaptureShader", "Error in setupShaders", e)
            throw RuntimeException("Failed to setup shaders", e)
        }
    }

    fun draw(textureId: Int, transformMatrix: FloatArray) {
        if (textureId <= 0) {
            Log.e("CaptureShader", "Invalid texture ID: $textureId")
            return
        }

        try {
            GLES20.glUseProgram(program)
            checkGlError("glUseProgram")

            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(positionHandle)
            checkGlError("Setup position attribute")

            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            checkGlError("Setup texCoord attribute")

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(textureHandle, 0)
            GLES20.glUniformMatrix4fv(transformMatrixHandle, 1, false, transformMatrix, 0)
            checkGlError("Setup uniforms")

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            checkGlError("glDrawArrays")

        } catch (e: Exception) {
            Log.e("CaptureShader", "Error during drawing", e)
        } finally {
            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            GLES20.glUseProgram(0)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        try {
            val shader = GLES20.glCreateShader(type)
            if (shader == 0) {
                throw RuntimeException("Failed to create shader")
            }

            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)
                Log.e("CaptureShader", "Shader compilation error: $error")
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Shader compilation failed: $error")
            }
            return shader
        } catch (e: Exception) {
            Log.e("CaptureShader", "Error in loadShader", e)
            throw RuntimeException("Failed to load shader", e)
        }
    }

    private fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val message = "$op: glError ${getErrorString(error)}"
            Log.e("CaptureShader", message)
            throw RuntimeException(message)
        }
    }

    private fun getErrorString(error: Int): String {
        return when (error) {
            GLES20.GL_INVALID_ENUM -> "GL_INVALID_ENUM"
            GLES20.GL_INVALID_VALUE -> "GL_INVALID_VALUE"
            GLES20.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
            GLES20.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
            GLES20.GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
            else -> "Unknown error $error"
        }
    }
} 