package mm.com.wavemoney.fullopencvtesting.document_scanner

/*
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import mm.com.wavemoney.fullopencvtesting.utils.ImageUtil
import mm.com.wavemoney.fullopencvtesting.utils.LightweightOpenCV
import kotlin.math.pow
import kotlin.math.sqrt

class LightweightFrameAnalyzer(
    private val previewView: PreviewView,
    private val onDocumentDetected: ((LightweightOpenCV.Mat, List<LightweightOpenCV.Point>, List<LightweightOpenCV.Point>) -> Unit)? = null,
    private val onPolygonDetected: ((List<LightweightOpenCV.Point>) -> Unit)? = null
) : ImageAnalysis.Analyzer {
    val imageUtils = ImageUtil()
    private val lightweightOpenCV = LightweightOpenCV()

    override fun analyze(image: ImageProxy) {
        try {
            // 1) Convert ImageProxy → Bitmap
            val bitmap = imageUtils.imageProxyToBitmap(image)

            // 2) Convert Bitmap → Mat (CV_8UC4)
            val srcMat = lightweightOpenCV.bitmapToMat(bitmap)

            // 3) Rotate the Mat so OpenCV sees "upright" (Portrait if needed)
            val rotatedMat = when (image.imageInfo.rotationDegrees) {
                90 -> lightweightOpenCV.rotate(srcMat, LightweightOpenCV.Core.ROTATE_90_CLOCKWISE)
                180 -> lightweightOpenCV.rotate(srcMat, LightweightOpenCV.Core.ROTATE_180)
                270 -> lightweightOpenCV.rotate(srcMat, LightweightOpenCV.Core.ROTATE_90_COUNTERCLOCKWISE)
                else -> srcMat.copyTo()
            }

            // 4) Preprocessing: to gray, equalize, blur, Canny, then close
            val gray = lightweightOpenCV.cvtColor(rotatedMat, LightweightOpenCV.Imgproc.COLOR_RGBA2GRAY)

            // 4a) Histogram equalization for better contrast (helpful under glare)
            val equalized = lightweightOpenCV.equalizeHist(gray)

            // 4b) Strong Gaussian blur to reduce noise (7×7 kernel)
            val blurred = lightweightOpenCV.gaussianBlur(equalized, 7)

            // 4c) Canny edge detection with lowered thresholds
            val canny = lightweightOpenCV.canny(blurred, 50.0, 250.0)

            // 4d) Morphological closing (5×5 rect) to fill tiny breaks in edges
            val closed = lightweightOpenCV.morphologyEx(canny, LightweightOpenCV.Imgproc.MORPH_CLOSE, 5)

            // 5) Find only external contours (ignoring text/details inside the card)
            val contours = lightweightOpenCV.findContours(closed)

            // 6) Loop through contours, keep the largest 4-point one
            var biggestContour: LightweightOpenCV.Contour? = null
            var maxArea = 0.0

            for (contour in contours) {
                val perimeter = lightweightOpenCV.arcLength(contour, true)
                val approx = lightweightOpenCV.approxPolyDP(contour, 0.02 * perimeter, true)

                if (approx.size == 4) {
                    val area = lightweightOpenCV.contourArea(contour)
                    Log.d("LightweightFrameAnalyzer", "🔍 Quad found: area=$area")
                    if (area > 10_000) {
                        val points = approx.toList()

                        // Calculate bounding rect to check dimensions
                        val boundingRect = lightweightOpenCV.boundingRect(contour)
                        val aspectRatio = boundingRect.width.toFloat() / boundingRect.height.toFloat()

                        val imgWidth = rotatedMat.width()
                        val imgHeight = rotatedMat.height()

                        val minWidth = imgWidth * 0.6   // at least 60% of width
                        val minHeight = imgHeight * 0.25 // at least 25% of height

                        val isAspectRatioValid = aspectRatio in 1.4..1.9
                        val isSizeValid =
                            boundingRect.width > minWidth && boundingRect.height > minHeight

                        if (isAspectRatioValid && isSizeValid && area > maxArea) {
                            maxArea = area
                            biggestContour = LightweightOpenCV.Contour(approx.toTypedArray())
                        } else {
                            Log.d(
                                "LightweightFrameAnalyzer",
                                "🛑 Rejected contour - AR=$aspectRatio, size=${boundingRect.width}x${boundingRect.height}"
                            )
                        }
                    }
                }
            }

            // If no valid 4-point contour, immediately clear the overlay
            if (biggestContour == null) {
                onPolygonDetected?.invoke(emptyList())
                image.close()
                return
            }

            // 7) We found a 4-point contour → order its corners TL, TR, BR, BL
            val orderedCorners = orderPoints(biggestContour.points.toList())

            // 8) Map those Mat‐space Points into PreviewView‐space, accounting for "centerCrop" scaling
            val mappedPoints = matPointsToViewPoints(orderedCorners, rotatedMat, previewView)
            onPolygonDetected?.invoke(mappedPoints)

            // 9) If you also want the top-down cropped Bitmap:
            // Pass both the original corners (for cropping) and the mapped points (for UI)
            onDocumentDetected?.invoke(rotatedMat, orderedCorners, mappedPoints)

        } catch (e: Exception) {
            Log.e("LightweightFrameAnalyzer", "Error in analyze: ${e.message}")
            onPolygonDetected?.invoke(emptyList())
        } finally {
            image.close()
        }
    }

    /**
     * Sort four points into [top-left, top-right, bottom-right, bottom-left].
     */
    private fun orderPoints(pts: List<LightweightOpenCV.Point>): List<LightweightOpenCV.Point> {
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
     * Convert a list of Points (in rotatedMat's pixel space) into PreviewView coordinates.
     * Accounts for PreviewView's CENTER_CROP scaling (fill‐center) and letterboxing.
     */
    private fun matPointsToViewPoints(
        matPoints: List<LightweightOpenCV.Point>,
        rotatedMat: LightweightOpenCV.Mat,
        previewView: PreviewView
    ): List<LightweightOpenCV.Point> {
        // 1) Dimensions of the OpenCV Mat (after rotation)
        val matWidth = rotatedMat.width().toFloat()
        val matHeight = rotatedMat.height().toFloat()

        // 2) Dimensions of the PreviewView on screen
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()

        // 3) PreviewView uses a "centerCrop" (FILL_CENTER) scaling by default.
        // scaleFactor = max(viewWidth / matWidth, viewHeight / matHeight)
        val scaleX = viewWidth / matWidth
        val scaleY = viewHeight / matHeight
        val scale = maxOf(scaleX, scaleY)

        // 4) After scaling, the Mat may be larger than the view in one dimension.
        //    We need to compute how much we "crop off" on each side.
        val scaledMatWidth = matWidth * scale
        val scaledMatHeight = matHeight * scale
        // If the Mat is wider than the view, dx > 0. If it's taller, dy > 0.
        val dx = (scaledMatWidth - viewWidth) / 2f
        val dy = (scaledMatHeight - viewHeight) / 2f

        // 5) For each point (px, py) in Mat‐space:
        //    viewX = px * scale - dx
        //    viewY = py * scale - dy
        return matPoints.map { pt ->
            val vx = pt.x * scale - dx
            val vy = pt.y * scale - dy
            LightweightOpenCV.Point(vx, vy)
        }
    }

    /**
     * Warp the 4-point contour (docContour) into a straight‐on rectangle.
     * Returns a new Mat of size (maxWidth × maxHeight) that is a top-down view.
     */
    private fun warpPerspective(src: LightweightOpenCV.Mat, docContour: LightweightOpenCV.Contour): LightweightOpenCV.Mat {
        val pts = docContour.points.toList()
        val ordered = orderPoints(pts)

        val widthTop = distance(ordered[0], ordered[1])
        val widthBottom = distance(ordered[2], ordered[3])
        val maxWidth = maxOf(widthTop, widthBottom).toInt()

        val heightLeft = distance(ordered[0], ordered[2])
        val heightRight = distance(ordered[1], ordered[3])
        val maxHeight = maxOf(heightLeft, heightRight).toInt()

        val dst = arrayOf(
            LightweightOpenCV.Point(0.0, 0.0),
            LightweightOpenCV.Point((maxWidth - 1).toDouble(), 0.0),
            LightweightOpenCV.Point(0.0, (maxHeight - 1).toDouble()),
            LightweightOpenCV.Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble())
        )

        val srcPts = ordered.toTypedArray()
        val transform = lightweightOpenCV.getPerspectiveTransform(srcPts, dst)
        return lightweightOpenCV.warpPerspective(src, transform, maxWidth, maxHeight)
    }

    /** Euclidean distance between two OpenCV Points */
    private fun distance(p1: LightweightOpenCV.Point, p2: LightweightOpenCV.Point): Double {
        return sqrt((p1.x - p2.x).pow(2.0) + (p1.y - p2.y).pow(2.0))
    }
}
 */