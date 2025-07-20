package mm.com.wavemoney.fullopencvtesting.document_scanner

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import mm.com.wavemoney.fullopencvtesting.utils.ImageUtil
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * FrameAnalyzer:
 *  - Converts each ImageProxy → Bitmap → Mat
 *  - Rotates the Mat to “upright” using rotationDegrees
 *  - Equalizes histogram, blurs, applies Canny, then closes small gaps
 *  - Finds only external contours (RETR_EXTERNAL)
 *  - Picks the largest 4-point contour whose area > 10_000 px
 *  - Calls onPolygonDetected(...) with either a List<Point> (size 4) or emptyList()
 *    so that the overlay can draw or clear immediately.
 *  - Optionally warps the Mat and calls onDocumentDetected(Bitmap).
 */
class FrameAnalyzer(
    private val previewView: PreviewView,
    private val onDocumentDetected: ((Mat, List<Point>, List<Point>) -> Unit)? = null,
    private val onPolygonDetected: ((List<Point>) -> Unit)? = null
) : ImageAnalysis.Analyzer {
    val imageUtils = ImageUtil()

    override fun analyze(image: ImageProxy) {
        // 1) Convert ImageProxy → Bitmap
        val bitmap = imageUtils.imageProxyToBitmap(image)

        // 2) Convert Bitmap → Mat (CV_8UC4)
        val srcMat = Mat().also { imageUtils.bitmapToMat(bitmap).copyTo(it) }

        // 3) Rotate the Mat so OpenCV sees “upright” (Portrait if needed)
        val rotatedMat = Mat()
        when (image.imageInfo.rotationDegrees) {
            90 -> Core.rotate(srcMat, rotatedMat, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(srcMat, rotatedMat, Core.ROTATE_180)
            270 -> Core.rotate(srcMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> srcMat.copyTo(rotatedMat)
        }

        // 4) Preprocessing: to gray, equalize, blur, Canny, then close
        val gray = Mat()
        Imgproc.cvtColor(rotatedMat, gray, Imgproc.COLOR_RGBA2GRAY)

        // 4a) Histogram equalization for better contrast (helpful under glare)
        Imgproc.equalizeHist(gray, gray)

        // 4b) Strong Gaussian blur to reduce noise (7×7 kernel)
        Imgproc.GaussianBlur(gray, gray, Size(7.0, 7.0), 0.0)

        // 4c) Canny edge detection with lowered thresholds
        Imgproc.Canny(gray, gray, 50.0, 250.0)

        // 4d) Morphological closing (5×5 rect) to fill tiny breaks in edges
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(gray, gray, Imgproc.MORPH_CLOSE, kernel)

        // 5) Find only external contours (ignoring text/details inside the card)
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            gray,
            contours,
            hierarchy,
            Imgproc.RETR_EXTERNAL,
            Imgproc.CHAIN_APPROX_SIMPLE
        )
        //For debug code to draw cropped area
        // Imgproc.drawContours(rotatedMat, contours, -1, Scalar(0.0, 255.0, 0.0), 2)

        // 6) Loop through contours, keep the largest 4-point one
        var biggestContour: MatOfPoint? = null
        var maxArea = 0.0

        for (c in contours) {
            val c2f = MatOfPoint2f(*c.toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)

            if (approx.total() == 4L) {
                val area = Imgproc.contourArea(approx)
                Log.d("FrameAnalyzer", "🔍 Quad found: area=$area")
                if (area > 10_000) {
                    val points = approx.toArray()

                    // Calculate bounding rect to check dimensions
                    val boundingRect = Imgproc.boundingRect(MatOfPoint(*points))
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
                        biggestContour = MatOfPoint(*points)
                    } else {
                        Log.d(
                            "FrameAnalyzer",
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
        val orderedCorners = orderPoints(biggestContour.toArray().toList())

        // 8) Map those Mat‐space Points into PreviewView‐space, accounting for "centerCrop" scaling
        val mappedPoints = matPointsToViewPoints(orderedCorners, rotatedMat, previewView)
        onPolygonDetected?.invoke(mappedPoints)

        // 9) If you also want the top-down cropped Bitmap:
        // Pass both the original corners (for cropping) and the mapped points (for UI)
        onDocumentDetected?.invoke(rotatedMat, orderedCorners, mappedPoints)

        image.close()
    }

    /**
     * Sort four points into [top-left, top-right, bottom-right, bottom-left].
     */
    private fun orderPoints(pts: List<Point>): List<Point> {
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
     * Convert a list of Points (in rotatedMat’s pixel space) into PreviewView coordinates.
     * Accounts for PreviewView’s CENTER_CROP scaling (fill‐center) and letterboxing.
     */
    private fun matPointsToViewPoints(
        matPoints: List<Point>,
        rotatedMat: Mat,
        previewView: PreviewView
    ): List<Point> {
        // 1) Dimensions of the OpenCV Mat (after rotation)
        val matWidth = rotatedMat.width().toFloat()
        val matHeight = rotatedMat.height().toFloat()

        // 2) Dimensions of the PreviewView on screen
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()

        // 3) PreviewView uses a “centerCrop” (FILL_CENTER) scaling by default.
        // scaleFactor = max(viewWidth / matWidth, viewHeight / matHeight)
        val scaleX = viewWidth / matWidth
        val scaleY = viewHeight / matHeight
        val scale = maxOf(scaleX, scaleY)

        // 4) After scaling, the Mat may be larger than the view in one dimension.
        //    We need to compute how much we “crop off” on each side.
        val scaledMatWidth = matWidth * scale
        val scaledMatHeight = matHeight * scale
        // If the Mat is wider than the view, dx > 0. If it’s taller, dy > 0.
        val dx = (scaledMatWidth - viewWidth) / 2f
        val dy = (scaledMatHeight - viewHeight) / 2f

        // 5) For each point (px, py) in Mat‐space:
        //    viewX = px * scale - dx
        //    viewY = py * scale - dy
        return matPoints.map { pt ->
            val vx = pt.x * scale - dx
            val vy = pt.y * scale - dy
            Point(vx, vy)
        }
    }

    /**
     * Warp the 4-point contour (docContour) into a straight‐on rectangle.
     * Returns a new Mat of size (maxWidth × maxHeight) that is a top-down view.
     */
    private fun warpPerspective(src: Mat, docContour: MatOfPoint): Mat {
        val pts = docContour.toArray().toList()
        val ordered = orderPoints(pts)

        val widthTop = distance(ordered[0], ordered[1])
        val widthBottom = distance(ordered[2], ordered[3])
        val maxWidth = maxOf(widthTop, widthBottom).toInt()

        val heightLeft = distance(ordered[0], ordered[2])
        val heightRight = distance(ordered[1], ordered[3])
        val maxHeight = maxOf(heightLeft, heightRight).toInt()

        val dst = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((maxWidth - 1).toDouble(), 0.0),
            Point(0.0, (maxHeight - 1).toDouble()),
            Point((maxWidth - 1).toDouble(), (maxHeight - 1).toDouble())
        )

        val srcPts = MatOfPoint2f(*ordered.toTypedArray())
        val transform = Imgproc.getPerspectiveTransform(srcPts, dst)
        val output = Mat()
        Imgproc.warpPerspective(
            src,
            output,
            transform,
            Size(maxWidth.toDouble(), maxHeight.toDouble())
        )
        return output
    }

    /** Euclidean distance between two OpenCV Points */
    private fun distance(p1: Point, p2: Point): Double {
        return sqrt((p1.x - p2.x).pow(2.0) + (p1.y - p2.y).pow(2.0))
    }
}

