package com.hension.havenx

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max
import kotlin.math.min

class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrixValues = FloatArray(9)
    private val imageMatrixInternal = Matrix()
    private var minScale = 1f
    private var maxScale = 5f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scale = detector.scaleFactor
            val current = currentScale()
            val target = (current * scale).coerceIn(minScale, maxScale)
            val delta = target / current
            imageMatrixInternal.postScale(delta, delta, detector.focusX, detector.focusY)
            fixTranslation()
            imageMatrix = imageMatrixInternal
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        post { resetImageMatrix() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) post { resetImageMatrix() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                dragging = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && dragging) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    imageMatrixInternal.postTranslate(dx, dy)
                    fixTranslation()
                    imageMatrix = imageMatrixInternal
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    private fun resetImageMatrix() {
        val d = drawable ?: return
        val vw = width.toFloat().takeIf { it > 0f } ?: return
        val vh = height.toFloat().takeIf { it > 0f } ?: return
        val dw = d.intrinsicWidth.toFloat().takeIf { it > 0f } ?: return
        val dh = d.intrinsicHeight.toFloat().takeIf { it > 0f } ?: return

        imageMatrixInternal.reset()
        minScale = min(vw / dw, vh / dh)
        maxScale = max(minScale * 5f, 2f)
        val tx = (vw - dw * minScale) / 2f
        val ty = (vh - dh * minScale) / 2f
        imageMatrixInternal.postScale(minScale, minScale)
        imageMatrixInternal.postTranslate(tx, ty)
        imageMatrix = imageMatrixInternal
    }

    private fun currentScale(): Float {
        imageMatrixInternal.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    private fun fixTranslation() {
        val d = drawable ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val dw = d.intrinsicWidth.toFloat()
        val dh = d.intrinsicHeight.toFloat()
        imageMatrixInternal.getValues(matrixValues)

        val scale = matrixValues[Matrix.MSCALE_X]
        val scaledW = dw * scale
        val scaledH = dh * scale
        var tx = matrixValues[Matrix.MTRANS_X]
        var ty = matrixValues[Matrix.MTRANS_Y]

        val minTx = if (scaledW > vw) vw - scaledW else (vw - scaledW) / 2f
        val maxTx = if (scaledW > vw) 0f else (vw - scaledW) / 2f
        val minTy = if (scaledH > vh) vh - scaledH else (vh - scaledH) / 2f
        val maxTy = if (scaledH > vh) 0f else (vh - scaledH) / 2f

        tx = tx.coerceIn(minTx, maxTx)
        ty = ty.coerceIn(minTy, maxTy)
        matrixValues[Matrix.MTRANS_X] = tx
        matrixValues[Matrix.MTRANS_Y] = ty
        imageMatrixInternal.setValues(matrixValues)
    }
}
