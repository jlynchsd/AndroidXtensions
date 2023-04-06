package com.androidxtensions.cameraxtension.gl

import com.androidxtensions.cameraxtension.Crop
import com.androidxtensions.cameraxtension.MirrorAxis
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlTransformationUtilTest {

    // region 0 rotation
    @Test
    fun `when rotated 0 degrees and cropping to left half returns correct coordinates`() {
        // y,x coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.5f,   //bottom right
            1.0f, 0.5f,   //top right
            0.0f, 1.0f,  //bottom left
            1.0f, 1.0f   //top left
        )
        val crop = Crop(0f, 0f, 0.5f, 1f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(0, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 0 degrees and cropping to right half returns correct coordinates`() {
        // y,x coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.0f,   //bottom right
            1.0f, 0.0f,   //top right
            0.0f, 0.5f,  //bottom left
            1.0f, 0.5f   //top left
        )
        val crop = Crop(0.5f, 0f, 0.5f, 1f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(0, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 0 degrees and cropping to top half returns correct coordinates`() {
        // y,x coordinate format
        val targetCoordinates = floatArrayOf(
            0.5f, 0.0f,   //bottom right
            1.0f, 0.0f,   //top right
            0.5f, 1.0f,  //bottom left
            1.0f, 1.0f   //top left
        )
        val crop = Crop(0f, 0f, 1f, 0.5f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(0, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 0 degrees and cropping to bottom half returns correct coordinates`() {
        // y,x coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.0f,   //bottom right
            0.5f, 0.0f,   //top right
            0.0f, 1.0f,  //bottom left
            0.5f, 1.0f   //top left
        )
        val crop = Crop(0f, 0.5f, 1f, 0.5f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(0, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 0 and mirroring horizontally returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(4f, 5f, 6f, 7f, 0f, 1f, 2f, 3f)

        val result = GlTransformationUtil.mirror(0, MirrorAxis.HORIZONTALLY, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 0 and mirroring vertically returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(2f, 3f, 0f, 1f, 6f, 7f, 4f, 5f)

        val result = GlTransformationUtil.mirror(0, MirrorAxis.VERTICALLY, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 0 and mirroring horizontally and vertically returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(6f, 7f, 4f, 5f, 2f, 3f, 0f, 1f)

        val result = GlTransformationUtil.mirror(0, MirrorAxis.BOTH, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 0 and not mirroring returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)

        val result = GlTransformationUtil.mirror(0, MirrorAxis.NONE, baseCoordinates)

        Assert.assertTrue(baseCoordinates contentEquals result)
    }

    // endregion

    // region 90 rotation
    @Test
    fun `when rotated 90 degrees and cropping to left half returns correct coordinates`() {
        // x,y coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.0f,   //bottom left
            0.5f, 0.0f,   //bottom right
            0.0f, 1.0f,  //top left
            0.5f, 1.0f   //top right
        )
        val crop = Crop(0f, 0f, 0.5f, 1f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(90, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 90 degrees and cropping to right half returns correct coordinates`() {
        // x,y coordinate format
        val targetCoordinates = floatArrayOf(
            0.5f, 0.0f,   //bottom left
            1.0f, 0.0f,   //bottom right
            0.5f, 1.0f,  //top left
            1.0f, 1.0f   //top right
        )
        val crop = Crop(0.5f, 0f, 0.5f, 1f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(90, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 90 degrees and cropping to top half returns correct coordinates`() {
        // x,y coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.5f,   //bottom left
            1.0f, 0.5f,   //bottom right
            0.0f, 1.0f,  //top left
            1.0f, 1.0f   //top right
        )
        val crop = Crop(0f, 0f, 1f, 0.5f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(90, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 90 degrees and cropping to bottom half returns correct coordinates`() {
        // x,y coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.0f,   //bottom left
            1.0f, 0.0f,   //bottom right
            0.0f, 0.5f,  //top left
            1.0f, 0.5f   //top right
        )
        val crop = Crop(0f, 0.5f, 1f, 0.5f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(90, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 90 and mirroring horizontally returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(2f, 3f, 0f, 1f, 6f, 7f, 4f, 5f)

        val result = GlTransformationUtil.mirror(90, MirrorAxis.HORIZONTALLY, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 90 and mirroring vertically returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(4f, 5f, 6f, 7f, 0f, 1f, 2f, 3f)

        val result = GlTransformationUtil.mirror(90, MirrorAxis.VERTICALLY, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 90 and mirroring horizontally and vertically returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(6f, 7f, 4f, 5f, 2f, 3f, 0f, 1f)

        val result = GlTransformationUtil.mirror(90, MirrorAxis.BOTH, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 90 and not mirroring returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)

        val result = GlTransformationUtil.mirror(90, MirrorAxis.NONE, baseCoordinates)

        Assert.assertTrue(baseCoordinates contentEquals result)
    }
    // endregion

    // region 180 rotation
    @Test
    fun `when rotated 180 degrees and cropping to left half returns correct coordinates`() {
        // y,x coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.5f,   //bottom right
            1.0f, 0.5f,   //top right
            0.0f, 1.0f,  //bottom left
            1.0f, 1.0f   //top left
        )
        val crop = Crop(0f, 0f, 0.5f, 1f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(180, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 180 degrees and cropping to right half returns correct coordinates`() {
        // y,x coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.0f,   //bottom right
            1.0f, 0.0f,   //top right
            0.0f, 0.5f,  //bottom left
            1.0f, 0.5f   //top left
        )
        val crop = Crop(0.5f, 0f, 0.5f, 1f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(180, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 180 degrees and cropping to top half returns correct coordinates`() {
        // y,x coordinate format
        val targetCoordinates = floatArrayOf(
            0.5f, 0.0f,   //bottom right
            1.0f, 0.0f,   //top right
            0.5f, 1.0f,  //bottom left
            1.0f, 1.0f   //top left
        )
        val crop = Crop(0f, 0f, 1f, 0.5f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(180, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 180 degrees and cropping to bottom half returns correct coordinates`() {
        // y,x coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.0f,   //bottom right
            0.5f, 0.0f,   //top right
            0.0f, 1.0f,  //bottom left
            0.5f, 1.0f   //top left
        )
        val crop = Crop(0f, 0.5f, 1f, 0.5f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(180, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 180 and mirroring horizontally returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(4f, 5f, 6f, 7f, 0f, 1f, 2f, 3f)

        val result = GlTransformationUtil.mirror(180, MirrorAxis.HORIZONTALLY, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 180 and mirroring vertically returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(2f, 3f, 0f, 1f, 6f, 7f, 4f, 5f)

        val result = GlTransformationUtil.mirror(180, MirrorAxis.VERTICALLY, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 180 and mirroring horizontally and vertically returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(6f, 7f, 4f, 5f, 2f, 3f, 0f, 1f)

        val result = GlTransformationUtil.mirror(180, MirrorAxis.BOTH, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 180 and not mirroring returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)

        val result = GlTransformationUtil.mirror(180, MirrorAxis.NONE, baseCoordinates)

        Assert.assertTrue(baseCoordinates contentEquals result)
    }
    // endregion

    // region 270 rotation
    @Test
    fun `when rotated 270 degrees and cropping to left half returns correct coordinates`() {
        // x,y coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.0f,   //bottom left
            0.5f, 0.0f,   //bottom right
            0.0f, 1.0f,  //top left
            0.5f, 1.0f   //top right
        )
        val crop = Crop(0f, 0f, 0.5f, 1f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(270, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 270 degrees and cropping to right half returns correct coordinates`() {
        // x,y coordinate format
        val targetCoordinates = floatArrayOf(
            0.5f, 0.0f,   //bottom left
            1.0f, 0.0f,   //bottom right
            0.5f, 1.0f,  //top left
            1.0f, 1.0f   //top right
        )
        val crop = Crop(0.5f, 0f, 0.5f, 1f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(270, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 270 degrees and cropping to top half returns correct coordinates`() {
        // x,y coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.5f,   //bottom left
            1.0f, 0.5f,   //bottom right
            0.0f, 1.0f,  //top left
            1.0f, 1.0f   //top right
        )
        val crop = Crop(0f, 0f, 1f, 0.5f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(270, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 270 degrees and cropping to bottom half returns correct coordinates`() {
        // x,y coordinate format
        val targetCoordinates = floatArrayOf(
            0.0f, 0.0f,   //bottom left
            1.0f, 0.0f,   //bottom right
            0.0f, 0.5f,  //top left
            1.0f, 0.5f   //top right
        )
        val crop = Crop(0f, 0.5f, 1f, 0.5f)
        val result = GlTransformationUtil.getRectangleTextureCoordinates(270, crop)
        Assert.assertTrue(targetCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 270 and mirroring horizontally returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(2f, 3f, 0f, 1f, 6f, 7f, 4f, 5f)

        val result = GlTransformationUtil.mirror(270, MirrorAxis.HORIZONTALLY, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 270 and mirroring vertically returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(4f, 5f, 6f, 7f, 0f, 1f, 2f, 3f)

        val result = GlTransformationUtil.mirror(270, MirrorAxis.VERTICALLY, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 270 and mirroring horizontally and vertically returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)
        val mirroredCoordinates = floatArrayOf(6f, 7f, 4f, 5f, 2f, 3f, 0f, 1f)

        val result = GlTransformationUtil.mirror(270, MirrorAxis.BOTH, baseCoordinates)

        Assert.assertTrue(mirroredCoordinates contentEquals result)
    }

    @Test
    fun `when rotated 270 and not mirroring returns correct coordinates`() {
        val baseCoordinates = floatArrayOf(0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f)

        val result = GlTransformationUtil.mirror(270, MirrorAxis.NONE, baseCoordinates)

        Assert.assertTrue(baseCoordinates contentEquals result)
    }
    // endregion
}