package com.example.minecraftcamera

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class PixelShader {
    private var program: Int = 0
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var textureHandle: Int = 0
    private var pixelSizeHandle: Int = 0
    
    private val vertexShaderCode = """
        attribute vec4 position;
        attribute vec2 texCoord;
        varying vec2 texCoordVarying;
        
        void main() {
            gl_Position = position;
            texCoordVarying = texCoord;
        }
    """

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec2 texCoordVarying;
        uniform sampler2D texture;
        uniform float pixelSize;
        
        void main() {
            vec2 texSize = vec2(640.0, 480.0);
            vec2 pixelated = floor(texCoordVarying * texSize / pixelSize) * pixelSize / texSize;
            gl_FragColor = texture2D(texture, pixelated);
        }
    """

    private val vertices = floatArrayOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f
    )

    private val texCoords = floatArrayOf(
        0.0f, 0.0f,
        1.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 1.0f
    )

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer

    init {
        setupBuffers()
        setupShaders()
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
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
        }

        positionHandle = GLES20.glGetAttribLocation(program, "position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "texCoord")
        textureHandle = GLES20.glGetUniformLocation(program, "texture")
        pixelSizeHandle = GLES20.glGetUniformLocation(program, "pixelSize")
    }

    fun draw(textureId: Int, pixelSize: Float) {
        GLES20.glUseProgram(program)

        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glUniform1f(pixelSizeHandle, pixelSize)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
} 