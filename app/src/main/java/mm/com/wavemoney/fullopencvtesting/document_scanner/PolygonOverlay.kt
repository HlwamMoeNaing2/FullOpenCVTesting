package mm.com.wavemoney.fullopencvtesting.document_scanner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import org.opencv.core.Point

/**
 * A simple custom View that draws a closed green Path connecting a List<Point>.
 * If the list is empty or has fewer than 4 points, nothing is drawn (i.e. overlay is cleared).
 */
class PolygonOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<Point>? = null

    private val paint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(20f, 15f), 0f)
    }

    /**
     * Call this from the UI thread to update or clear the polygon:
     *  - If points.size >= 4, draws a closed‐loop connecting them in order.
     *  - If points.isEmpty(), we simply do invalidate(), and onDraw() draws nothing (clearing the overlay).
     */
    fun setPoints(points: List<Point>) {
        this.points = points
        invalidate()
    }

    /**
     * Returns true if the polygon (green dash) is currently being drawn.
     */
    fun isPolygonDrawn(): Boolean {
        return (points?.size ?: 0) >= 4
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        points?.let { pts ->
            if (pts.size >= 4) {
                val path = Path().apply {
                    moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
                    for (i in 1 until pts.size) {
                        lineTo(pts[i].x.toFloat(), pts[i].y.toFloat())
                    }
                    close()
                }
                canvas.drawPath(path, paint)
            }
        }
    }
}
