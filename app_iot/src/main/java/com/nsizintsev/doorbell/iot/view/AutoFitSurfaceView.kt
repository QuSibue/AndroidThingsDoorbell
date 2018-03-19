package com.nsizintsev.doorbell.iot.view

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.View

/**
 * Created by nsizintsev on 3/12/2018.
 */

class AutoFitSurfaceView : SurfaceView {

    private var mRatioWidth = 0

    private var mRatioHeight = 0

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context,
                attrs: AttributeSet,
                defStyleAttr: Int,
                defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    fun setAspectRatio(width: Int, height: Int) {
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Size cannot be negative.")
        }
        mRatioWidth = width
        mRatioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)

        val fWidth: Int
        val fHeight: Int

        if (0 == mRatioWidth || 0 == mRatioHeight) {
            fWidth = width
            fHeight = height
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                fWidth = width
                fHeight = width * mRatioHeight / mRatioWidth
            } else {
                fWidth = height * mRatioWidth / mRatioHeight
                fHeight = height
            }
        }
        setMeasuredDimension(fWidth, fHeight)
        holder.setFixedSize(fWidth, fHeight)
    }

}
