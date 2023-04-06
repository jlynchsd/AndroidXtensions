package com.androidxtensions.cameraxtension.gl

import com.androidxtensions.cameraxtension.Crop
import com.androidxtensions.cameraxtension.MirrorAxis

object GlTransformationUtil {
    fun getRectangleTextureCoordinates(rotation: Int, crop: Crop) =
        when (rotation) {
            //x,y; bottom left, bottom right, top left, top right
            90, 270 -> floatArrayOf(
                crop.x,
                (1f - (crop.height + crop.y)),

                (crop.x + crop.width),
                (1f - (crop.height + crop.y)),

                crop.x,
                (1f - crop.y),

                (crop.x + crop.width),
                (1f - crop.y)
            )
            //y,x; bottom right, top right, bottom left, top left
            else -> floatArrayOf(
                (1f - (crop.height + crop.y)),
                (1f - (crop.width + crop.x)),

                (1f - crop.y),
                (1f - (crop.width + crop.x)),

                (1f - (crop.height + crop.y)),
                (1f - crop.x),

                (1f - crop.y),
                (1f - crop.x)
            )
        }

    fun mirror(rotation: Int, axis: MirrorAxis, rectangleTextureCoordinates: FloatArray): FloatArray {
        val rotated = (rotation == 90 || rotation == 270)
        val adjustedAxis = if (rotated) {
            when (axis) {
                MirrorAxis.HORIZONTALLY -> MirrorAxis.VERTICALLY
                MirrorAxis.VERTICALLY -> MirrorAxis.HORIZONTALLY
                else -> axis
            }
        } else {
            axis
        }
        return when (adjustedAxis) {
            MirrorAxis.HORIZONTALLY ->
                rectangleTextureCoordinates.sliceArray(4 .. 7) +
                        rectangleTextureCoordinates.sliceArray(0 .. 3)

            MirrorAxis.VERTICALLY ->
                rectangleTextureCoordinates.sliceArray(2 .. 3) +
                        rectangleTextureCoordinates.sliceArray(0 .. 1) +
                        rectangleTextureCoordinates.sliceArray(6 .. 7) +
                        rectangleTextureCoordinates.sliceArray(4 .. 5)

            MirrorAxis.BOTH ->
                rectangleTextureCoordinates.sliceArray(6 .. 7) +
                        rectangleTextureCoordinates.sliceArray(4 .. 5) +
                        rectangleTextureCoordinates.sliceArray(2 .. 3) +
                        rectangleTextureCoordinates.sliceArray(0 .. 1)

            MirrorAxis.NONE -> rectangleTextureCoordinates
        }
    }


}