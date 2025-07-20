package mm.com.wavemoney.fullopencvtesting.document_scanner

/*
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import mm.com.wavemoney.fullopencvtesting.utils.ImageUtil
import mm.com.wavemoney.fullopencvtesting.utils.SimpleImageProcessor
import kotlin.math.max

class SimpleFrameAnalyzer(
    private val previewView: PreviewView,
    private val onDocumentDetected: ((Bitmap, List<SimpleImageProcessor.Point>, List<SimpleImageProcessor.Point>) -> Unit)? = null,
    private val onPolygonDetected: ((List<SimpleImageProcessor.Point>) -> Unit)? = null
) : ImageAnalysis.Analyzer {
    val imageUtils = ImageUtil()
    private val imageProcessor = SimpleImageProcessor()

    override fun analyze(image: ImageProxy) {
        try {
            // 1) Convert ImageProxy → Bitmap
            val bitmap = imageUtils.imageProxyToBitmap(image)

            // 2) Rotate the bitmap if needed
            val rotatedBitmap = when (image.imageInfo.rotationDegrees) {
                90 -> imageProcessor.rotateBitmap(bitmap, 90f)
                180 -> imageProcessor.rotateBitmap(bitmap, 180f)
                270 -> imageProcessor.rotateBitmap(bitmap, 270f)
                else -> bitmap
            }

            // 3) Preprocessing: to gray, blur, edge detection
            val gray = imageProcessor.toGrayscale(rotatedBitmap)
            val blurred = imageProcessor.gaussianBlur(gray, 3)
            val edges = imageProcessor.detectEdges(blurred)

            // 4) Find contours
            val contours = imageProcessor.findContours(edges)

            // 5) Find the best quadrilateral contour
            var bestContour: List<SimpleImageProcessor.Point>? = null
            var maxArea = 0.0

            for (contour in contours) {
                val area = imageProcessor.contourArea(contour)
                if (area > 10_000) { // Minimum area threshold
                    val approx = imageProcessor.approximatePolygon(contour, 0.02 * contour.size)

                    if (approx.size == 4) {
                        val boundingRect = imageProcessor.boundingRect(approx)
                        val aspectRatio = boundingRect.width.toFloat() / boundingRect.height.toFloat()

                        val imgWidth = rotatedBitmap.width
                        val imgHeight = rotatedBitmap.height

                        val minWidth = imgWidth * 0.6   // at least 60% of width
                        val minHeight = imgHeight * 0.25 // at least 25% of height

                        val isAspectRatioValid = aspectRatio in 1.4..1.9
                        val isSizeValid = boundingRect.width > minWidth && boundingRect.height > minHeight

                        if (isAspectRatioValid && isSizeValid && area > maxArea) {
                            maxArea = area
                            bestContour = approx
                        }
                    }
                }
            }

            // If no valid 4-point contour, clear the overlay
            if (bestContour == null) {
                onPolygonDetected?.invoke(emptyList())
                image.close()
                return
            }

            // 6) Order the corners
            val orderedCorners = orderPoints(bestContour)

            // 7) Map to PreviewView coordinates
            val mappedPoints = mapToPreviewViewCoordinates(orderedCorners, rotatedBitmap, previewView)
            onPolygonDetected?.invoke(mappedPoints)

            // 8) Pass the detected document
            onDocumentDetected?.invoke(rotatedBitmap, orderedCorners, mappedPoints)

        } catch (e: Exception) {
            Log.e("SimpleFrameAnalyzer", "Error in analyze: ${e.message}")
            onPolygonDetected?.invoke(emptyList())
        } finally {
            image.close()
        }
    }

    /**
     * Sort four points into [top-left, top-right, bottom-right, bottom-left].
     */
    private fun orderPoints(pts: List<SimpleImageProcessor.Point>): List<SimpleImageProcessor.Point> {
        // sum = x + y; diff = y - x
        val sumSorted = pts.sortedBy { it.y + it.x }
        val diffSorted = pts.sortedBy { it.y - it.x }
        return listOf(
            sumSorted[0],   // smallest sum  = top-left
            diffSorted[0],  // smallest diff = top-right
            sumSorted[3],   // largest sum   = bottom-right
            diffSorted[3]   // largest diff  = bottom-left
        )
    }

    /**
     * Convert image coordinates to PreviewView coordinates
     */
    private fun mapToPreviewViewCoordinates(
        imagePoints: List<SimpleImageProcessor.Point>,
        bitmap: Bitmap,
        previewView: PreviewView
    ): List<SimpleImageProcessor.Point> {
        val imgWidth = bitmap.width.toFloat()
        val imgHeight = bitmap.height.toFloat()
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()

        // Calculate scaling to fit the image in the view
        val scaleX = viewWidth / imgWidth
        val scaleY = viewHeight / imgHeight
        val scale = max(scaleX, scaleY)

        val scaledImgWidth = imgWidth * scale
        val scaledImgHeight = imgHeight * scale
        val dx = (scaledImgWidth - viewWidth) / 2f
        val dy = (scaledImgHeight - viewHeight) / 2f

        return imagePoints.map { pt ->
            val vx = pt.x * scale - dx
            val vy = pt.y * scale - dy
            SimpleImageProcessor.Point(vx, vy)
        }
    }
}
 */