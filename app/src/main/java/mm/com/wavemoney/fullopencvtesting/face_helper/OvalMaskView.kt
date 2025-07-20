package mm.com.wavemoney.fullopencvtesting.face_helper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.toColorInt
import mm.com.wavemoney.fullopencvtesting.R


class OvalMaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var borderColor: Int = "#009E3D".toColorInt()
    private val ovalRect = RectF()

    private val maskPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint().apply {
        color = Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val borderPaint = Paint().apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Aspect ratio: 226:292 = 0.773
    private val ovalAspectRatio = 226f / 292f

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.OvalMaskView,
            0, 0
        ).apply {
            try {
                borderColor = getColor(R.styleable.OvalMaskView_borderColor, borderColor)
                borderPaint.color = borderColor
            } finally {
                recycle()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layerId = canvas.saveLayer(null, null)

        // Fill whole view with semi-black
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)

        // Adjust oval to fit view while preserving aspect ratio
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val viewRatio = viewWidth / viewHeight

        val (ovalWidth, ovalHeight) = if (viewRatio > ovalAspectRatio) {
            // View is too wide -> limit by height
            val h = viewHeight * 0.95f
            val w = h * ovalAspectRatio
            w to h
        } else {
            // View is too tall -> limit by width
            val w = viewWidth * 0.95f
            val h = w / ovalAspectRatio
            w to h
        }

        val left = (viewWidth - ovalWidth) / 2f
        val top = (viewHeight - ovalHeight) / 2f
        val right = left + ovalWidth
        val bottom = top + ovalHeight

        ovalRect.set(left, top, right, bottom)

        // Transparent oval
        canvas.drawOval(ovalRect, clearPaint)

        // Oval border
        canvas.drawOval(ovalRect, borderPaint)

        canvas.restoreToCount(layerId)
    }

    fun setBorderColor(@ColorInt color: Int) {
        borderColor = color
        borderPaint.color = color
        invalidate()
    }
}
