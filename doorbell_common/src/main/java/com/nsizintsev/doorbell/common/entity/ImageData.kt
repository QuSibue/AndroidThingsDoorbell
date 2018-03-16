package com.nsizintsev.doorbell.common.entity

import android.graphics.Rect

/**
 * Created by nsizintsev on 3/13/2018.
 */

class ImageData(val width: Int,
                val height: Int,
                val format: Int,
                val cropRect: Rect,
                val byteArray: ByteArray)
