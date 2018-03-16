package com.nsizintsev.doorbell.iot.util

import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import java.util.*
import kotlin.math.sign


/**
 * Created by nsizintsev on 3/12/2018.
 */

object CameraUtil {

    val orientations = SparseIntArray()

    init {
        orientations.append(Surface.ROTATION_0, 90)
        orientations.append(Surface.ROTATION_90, 0)
        orientations.append(Surface.ROTATION_180, 270)
        orientations.append(Surface.ROTATION_270, 180)
    }

    fun chooseOptimalSize(choices: Array<Size>,
                          textureViewWidth: Int,
                          textureViewHeight: Int,
                          maxWidth: Int,
                          maxHeight: Int,
                          aspectRatio: Size): Size {

        val bigEnough = ArrayList<Size>()
        val notBigEnough = ArrayList<Size>()
        val w = aspectRatio.width
        val h = aspectRatio.height

        for (option in choices) {
            if (option.width <= maxWidth && option.height <= maxHeight
                    && option.height == option.width * h / w) {
                if (option.width >= textureViewWidth && option.height >= textureViewHeight) {
                    bigEnough.add(option)
                } else {
                    notBigEnough.add(option)
                }
            }
        }

        if (bigEnough.size > 0) {
            return Collections.min(bigEnough, CompareSizesByArea())
        } else if (notBigEnough.size > 0) {
            return Collections.max(notBigEnough, CompareSizesByArea())
        } else {
            return choices[0]
        }
    }

    fun getCaptureOrientation(screenRotation: Int, sensorRotation: Int): Int {
        return (orientations.get(screenRotation) + sensorRotation + 270) % 360
    }

    class CompareSizesByArea : Comparator<Size> {

        override fun compare(lhs: Size, rhs: Size): Int {
            return (lhs.width.toLong() * lhs.height.toLong() - rhs.width.toLong() * rhs.height.toLong()).sign
        }

    }

}
