package com.androidxtensions.cameraxtension.gl

import android.util.Size
import com.androidxtensions.cameraxtension.CameraConfiguration
import io.mockk.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowSize::class])
class ImageAnalysisHandlerTest {

    @Before
    fun setup() {
        mockGles20()
        mockGles30()
    }

    @After
    fun cleanup() {
        unmockkAll()
    }

    // region GLES30

    @Test
    fun `when created with GLES3 makes pbuffer surface`() {
        val mockApi = mockEglApi(3)
        val imageAnalysisHandler = ImageAnalysisHandler(
            mockApi, CameraConfiguration.Builder().build(), Size(0, 0), 0, 0
        )

        verify { mockApi.createPbufferSurface(any(), any()) }
    }

    @Test
    fun `when generating image with GLES3 but double buffer not ready does not call analysis`() {
        val mockApi = mockEglApi(3)
        var analysisCalled = false
        val configuration = CameraConfiguration.Builder().imageAnalysis {
            it.use {
                analysisCalled = true
            }
        }.build()

        val imageAnalysisHandler = ImageAnalysisHandler(
            mockApi, configuration, Size(0, 0), 0, 0
        )

        imageAnalysisHandler.generateAnalysisImage(0, floatArrayOf())

        Assert.assertFalse(analysisCalled)
    }

    @Test
    fun `when generating image with GLES3 calls analysis with data`() {
        val mockApi = mockEglApi(3)
        var analysisCalled = false
        val configuration = CameraConfiguration.Builder().imageAnalysis {
            it.use {
                analysisCalled = true
            }
        }.build()

        val imageAnalysisHandler = ImageAnalysisHandler(
            mockApi, configuration, Size(0, 0), 0, 0
        )

        imageAnalysisHandler.generateAnalysisImage(0, floatArrayOf())
        imageAnalysisHandler.generateAnalysisImage(0, floatArrayOf())

        Assert.assertTrue(analysisCalled)
    }

    @Test
    fun `when generating image with GLES3 but previous image is not released does not call analysis again`() {
        val mockApi = mockEglApi(3)
        var analysisCalled = 0
        val configuration = CameraConfiguration.Builder().imageAnalysis {
            analysisCalled++
        }.build()

        val imageAnalysisHandler = ImageAnalysisHandler(
            mockApi, configuration, Size(0, 0), 0, 0
        )

        imageAnalysisHandler.generateAnalysisImage(0, floatArrayOf())
        imageAnalysisHandler.generateAnalysisImage(0, floatArrayOf())
        imageAnalysisHandler.generateAnalysisImage(0, floatArrayOf())

        Assert.assertEquals(1, analysisCalled)
    }


    @Test
    fun `when released with GLES3 destroys pbuffer surface`() {
        val mockApi = mockEglApi(3)
        val imageAnalysisHandler = ImageAnalysisHandler(
            mockApi, CameraConfiguration.Builder().build(), Size(0, 0), 0, 0
        )
        imageAnalysisHandler.release()

        verify { mockApi.destroySurface(any()) }
    }

    // endregion

    // region GLES20

    @Test
    fun `when created with GLES2 makes pbuffer surface`() {
        val mockApi = mockEglApi(2)
        val imageAnalysisHandler = ImageAnalysisHandler(
            mockApi, CameraConfiguration.Builder().build(), Size(0, 0), 0, 0
        )

        verify { mockApi.createPbufferSurface(any(), any()) }
    }

    @Test
    fun `when generating image with GLES2 calls analysis with data`() {
        val mockApi = mockEglApi(2)
        var analysisCalled = false
        val configuration = CameraConfiguration.Builder().imageAnalysis {
            it.use {
                analysisCalled = true
            }
        }.build()

        val imageAnalysisHandler = ImageAnalysisHandler(
            mockApi, configuration, Size(0, 0), 0, 0
        )

        imageAnalysisHandler.generateAnalysisImage(0, floatArrayOf())

        Assert.assertTrue(analysisCalled)
    }

    @Test
    fun `when generating image with GLES2 but previous image is not released does not call analysis again`() {
        val mockApi = mockEglApi(2)
        var analysisCalled = 0
        val configuration = CameraConfiguration.Builder().imageAnalysis {
            analysisCalled++
        }.build()

        val imageAnalysisHandler = ImageAnalysisHandler(
            mockApi, configuration, Size(0, 0), 0, 0
        )

        imageAnalysisHandler.generateAnalysisImage(0, floatArrayOf())
        imageAnalysisHandler.generateAnalysisImage(0, floatArrayOf())

        Assert.assertEquals(1, analysisCalled)
    }


    @Test
    fun `when released with GLES2 destroys pbuffer surface`() {
        val mockApi = mockEglApi(2)
        val imageAnalysisHandler = ImageAnalysisHandler(
            mockApi, CameraConfiguration.Builder().build(), Size(0, 0), 0, 0
        )
        imageAnalysisHandler.release()

        verify { mockApi.destroySurface(any()) }
    }

    // endregion

    private fun mockEglApi(version: Int): EglApi {
        val mockEglApi = mockk<EglApi>()

        every {
            mockEglApi.glVersion
        } returns version

        every {
            mockEglApi.createPbufferSurface(any(), any())
        } returns mockk()

        every {
            mockEglApi.makeCurrent(any())
        } just runs

        every {
            mockEglApi.destroySurface(any())
        } just runs

        return mockEglApi
    }

    private fun mockGles30() {
        mockkObject(Gles30Api)
        mockkObject(BufferFactory)

        every {
            Gles30Api.glGenBuffers(any(), any())
        } just runs

        every {
            Gles30Api.glBindBuffer(any(), any())
        } just runs

        every {
            Gles30Api.glBufferData(any(), any(), any(), any())
        } just runs

        val mockBuffer = mockk<ByteBuffer>()
        every {
            mockBuffer.order(any())
        } returns mockBuffer
        every {
            mockBuffer.put(any<ByteBuffer>())
        } returns mockBuffer
        every {
            mockBuffer.remaining()
        } returns 0
        every {
            mockBuffer.clear()
        } returns mockBuffer
        every {
            mockBuffer.array()
        } returns byteArrayOf()
        every {
            BufferFactory.allocateBuffer(any())
        } returns mockBuffer

        every {
            Gles30Api.glMapBufferRange(any(), any(), any(), any())
        } returns mockBuffer

        every {
            Gles30Api.glUnmapBuffer(any())
        } returns true

        every {
            Gles30Api.readPixelsToBuffer(any(), any(), any(), any(), any(), any())
        } just runs
    }
}