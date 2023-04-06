package com.androidxtensions.cameraxtension.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.androidxtensions.cameraxtension.TransformationConfiguration
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Owns the lifecycle of shader programs, handles draw calls.
 */
internal class TextureWriter(
    rotation: Int,
    transformationConfiguration: TransformationConfiguration
) {

    // Handles to the GL program and various components of it.
    private var programHandle = 0
    private val uMVPMatrixLocation: Int
    private val uTexMatrixLocation: Int
    private val aPositionLocation: Int
    private val aTextureCoordLocation: Int
    private val textureCoordArray =
        toFloatBuffer(
            GlTransformationUtil.mirror(
                rotation,
                transformationConfiguration.mirror,
                GlTransformationUtil.getRectangleTextureCoordinates(
                    rotation,
                    transformationConfiguration.crop
                )
            )
        )

    init {
        programHandle = if (transformationConfiguration.edgeDetect) {
            createProgram(FRAGMENT_SHADER_EDGE_DETECT)
        } else {
            createProgram(FRAGMENT_SHADER_DEFAULT)
        }

        if (programHandle == 0) {
            throw RuntimeException("Unable to create program")
        }

        aPositionLocation = Gles20Api.glGetAttribLocation(programHandle, "aPosition")
        aTextureCoordLocation = Gles20Api.glGetAttribLocation(programHandle, "aTextureCoord")
        uMVPMatrixLocation = Gles20Api.glGetUniformLocation(programHandle, "uMVPMatrix")
        uTexMatrixLocation = Gles20Api.glGetUniformLocation(programHandle, "uTexMatrix")

        if (aPositionLocation < 0 ||
            aTextureCoordLocation < 0 ||
            uMVPMatrixLocation < 0 ||
            uTexMatrixLocation < 0) {
            throw RuntimeException("Unable to load program")
        }
    }

    /**
     * Deletes the existing program.
     */
    fun release() {
        Gles20Api.glDeleteProgram(programHandle)
        programHandle = -1
    }

    /**
     * Create a texture for drawing.
     */
    fun createTexture(): Int {
        val textures = IntArray(1)
        Gles20Api.glGenTextures(1, textures, 0)
        checkGlesError("glGenTextures")
        val texId = textures[0]
        Gles20Api.glBindTexture(TEXTURE_TARGET, texId)
        checkGlesError("glBindTexture $texId")
        Gles20Api.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        Gles20Api.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        Gles20Api.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        Gles20Api.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        checkGlesError("glTexParameter")
        return texId
    }

    /**
     * Draw to the provided texture.
     */
    fun draw(textureId: Int, texMatrix: FloatArray, mvpMatrix: FloatArray) {
        checkGlesError("draw start")

        // Select the program.
        Gles20Api.glUseProgram(programHandle)
        checkGlesError("glUseProgram")

        // Set the texture.
        Gles20Api.glActiveTexture(GLES20.GL_TEXTURE0)
        Gles20Api.glBindTexture(TEXTURE_TARGET, textureId)

        // Copy the model / view / projection matrix over.
        Gles20Api.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, mvpMatrix, 0)
        checkGlesError("glUniformMatrix4fv")

        // Copy the texture transformation matrix over.
        Gles20Api.glUniformMatrix4fv(uTexMatrixLocation, 1, false, texMatrix, 0)
        checkGlesError("glUniformMatrix4fv")

        // Enable the "aPosition" vertex attribute.
        Gles20Api.glEnableVertexAttribArray(aPositionLocation)
        checkGlesError("glEnableVertexAttribArray")

        // Connect vertexBuffer to "aPosition".
        Gles20Api.glVertexAttribPointer(
            aPositionLocation, COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false, STRIDE, RECTANGLE_BUFFER
        )
        checkGlesError("glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute.
        Gles20Api.glEnableVertexAttribArray(aTextureCoordLocation)
        checkGlesError("glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord".
        Gles20Api.glVertexAttribPointer(
            aTextureCoordLocation, 2,
            GLES20.GL_FLOAT, false, STRIDE, textureCoordArray
        )
        checkGlesError("glVertexAttribPointer")

        // Draw the rect.
        Gles20Api.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTEX_COUNT)
        checkGlesError("glDrawArrays")

        // Done -- disable vertex array, texture, and program.
        Gles20Api.glDisableVertexAttribArray(aPositionLocation)
        Gles20Api.glDisableVertexAttribArray(aTextureCoordLocation)
        Gles20Api.glBindTexture(TEXTURE_TARGET, GLES20.GL_NONE)
        Gles20Api.glUseProgram(GLES20.GL_NONE)
    }

    private fun createProgram(fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        if (vertexShader == 0) {
            return 0
        }
        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) {
            return 0
        }
        var program = Gles20Api.glCreateProgram()
        checkGlesError("glCreateProgram")
        Gles20Api.glAttachShader(program, vertexShader)
        checkGlesError("glAttachShader")
        Gles20Api.glAttachShader(program, pixelShader)
        checkGlesError("glAttachShader")
        Gles20Api.glLinkProgram(program)
        val linkStatus = IntArray(1)
        Gles20Api.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Gles20Api.glDeleteProgram(program)
            program = 0
        }
        return program
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = Gles20Api.glCreateShader(shaderType)
        checkGlesError("glCreateShader type=$shaderType")
        Gles20Api.glShaderSource(shader, source)
        Gles20Api.glCompileShader(shader)
        val compiled = IntArray(1)
        Gles20Api.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Gles20Api.glDeleteShader(shader)
            shader = 0
        }
        return shader
    }

    private fun checkGlesError(op: String) {
        val error = Gles20Api.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val msg = op + ": glError 0x" + Integer.toHexString(error)
            throw RuntimeException(msg)
        }
    }

    companion object {
        private const val BYTES_PER_FLOAT = 4
        private const val TEXTURE_TARGET = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        private const val VERTEX_COUNT = 4
        private const val COORDS_PER_VERTEX = 2
        private const val STRIDE = BYTES_PER_FLOAT * 2

        private val RECTANGLE_BUFFER = toFloatBuffer(
            floatArrayOf(
                -1.0f, -1.0f,
                1.0f, -1.0f,
                -1.0f, 1.0f,
                1.0f, 1.0f
            )
        )

        private const val VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uTexMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "    gl_Position = uMVPMatrix * aPosition;\n" +
                "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                "}\n"

        private const val FRAGMENT_SHADER_DEFAULT = "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                "}\n"

        private const val FRAGMENT_SHADER_EDGE_DETECT =
            "#extension GL_OES_EGL_image_external : require\n" +
                "#extension GL_OES_standard_derivatives : enable\n" +
                "precision mediump float;\n" +
                "varying vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                " \n" +
                "vec4 edge(in vec4 color) {\n" +
                "    float gray = color.r * 0.21 + color.g * 0.71 + color.b * 0.07;\n" +
                "    return vec4(vec3(smoothstep(0.05, 0.08, length(vec2(dFdx(gray), dFdy(gray))))), 1.0);\n" +
                "}\n" +
                "void main() {\n" +
                "    vec3 offset = vec3(0.0, 1.3846153846, 3.2307692308);\n" +
                "    vec3 weight = vec3(0.2270270270, 0.3162162162, 0.0702702703);\n" +
                "    vec4 color = edge(texture2D(sTexture, vTextureCoord)) * weight[0];\n" +
                "    for (int i=1; i<3; i++) {\n" +
                "        color +=\n" +
                "            edge(texture2D(sTexture, vTextureCoord + vec2(offset[i], offset[i])))\n" +
                "                * weight[i];\n" +
                "        color +=\n" +
                "            edge(texture2D(sTexture, vTextureCoord - vec2(offset[i], offset[i])))\n" +
                "                * weight[i];\n" +
                "    }\n" +
                    "gl_FragColor = color;\n" +
                "}"

        fun toFloatBuffer(data: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(data.size * BYTES_PER_FLOAT).let { byteBuffer ->
                byteBuffer.order(ByteOrder.nativeOrder())
                byteBuffer.asFloatBuffer().put(data).position(0) as FloatBuffer
            }
    }
}