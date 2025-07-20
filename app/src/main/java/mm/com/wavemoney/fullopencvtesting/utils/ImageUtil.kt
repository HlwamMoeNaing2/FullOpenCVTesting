package mm.com.wavemoney.fullopencvtesting.utils


import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import mm.com.wavemoney.fullopencvtesting.extensions.distance
import mm.com.wavemoney.fullopencvtesting.extensions.toOpenCVPoint
import mm.com.wavemoney.fullopencvtesting.models.Quad
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt


class ImageUtil  {
    private fun getImageMatrixFromBitmap(bitmap: Bitmap): Mat {
        val image = Mat()

        // Ensure bitmap is ARGB_8888 and mutable
        val safeBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap.config?.let { bitmap.copy(it, true) }
        }

        // Convert to OpenCV Mat
        Utils.bitmapToMat(safeBitmap, image)

        // Convert from RGBA to RGB (OpenCV reads Bitmap as RGBA)
        Imgproc.cvtColor(image, image, Imgproc.COLOR_RGBA2RGB)

        return image
    }

    /**
     * take a photo with a document, crop everything out but document, and force it to display
     * as a rectangle
     *
     * @param photoFilePath original image is saved here
     * @param corners the 4 document corners
     * @return bitmap with cropped and warped document
     */
    fun crop(bitmap: Bitmap, corners: Quad): Bitmap {
        val image = this.getImageMatrixFromBitmap(bitmap)
        val tLC = corners.topLeftCorner.toOpenCVPoint()
        val tRC = corners.topRightCorner.toOpenCVPoint()
        val bRC = corners.bottomRightCorner.toOpenCVPoint()
        val bLC = corners.bottomLeftCorner.toOpenCVPoint()
        // Calculate the document edge distances. The user might take a skewed photo of the
        // document, so the top left corner to top right corner distance might not be the same
        // as the bottom left to bottom right corner. We could take an average of the 2, but
        // this takes the smaller of the 2. It does the same for height.
        val width = min(tLC.distance(tRC), bLC.distance(bRC))
        val height = min(tLC.distance(bLC), tRC.distance(bRC))

        // create empty image matrix with cropped and warped document width and height
        val croppedImage = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width, 0.0),
            Point(width, height),
            Point(0.0, height),
        )

        // This crops the document out of the rest of the photo. Since the user might take a
        // skewed photo instead of a straight on photo, the document might be rotated and
        // skewed. This corrects that problem. output is an image matrix that contains the
        // corrected image after this fix.
        val output = Mat()
        Imgproc.warpPerspective(
            image,
            output,
            Imgproc.getPerspectiveTransform(
                MatOfPoint2f(tLC, tRC, bRC, bLC),
                croppedImage
            ),
            Size(width, height)
        )

        // convert output image matrix to bitmap
        val croppedBitmap = createBitmap(output.cols(), output.rows())
        Utils.matToBitmap(output, croppedBitmap)

        // Auto-rotate to landscape if the cropped image is in portrait orientation
        val finalBitmap = autoRotateToLandscape(croppedBitmap)
        return finalBitmap
    }

    /**
     * Automatically rotate the image to landscape orientation if it's in portrait
     */
    private fun autoRotateToLandscape(bitmap: Bitmap): Bitmap {
        Log.d("@ROTATE", "Checking rotation for bitmap: ${bitmap.width}x${bitmap.height}")

        // If the image is already in landscape (width >= height), return as is
        if (bitmap.width >= bitmap.height) {
            Log.d("@ROTATE", "Already landscape, no rotation needed")
            return bitmap
        }

        // If the image is in portrait (height > width), rotate it 90 degrees clockwise
        Log.d("@ROTATE", "Portrait detected, rotating 90° clockwise")

        val matrix = Matrix()
        matrix.postRotate(90f)

        try {
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )

            Log.d(
                "@ROTATE",
                "Successfully rotated to: ${rotatedBitmap.width}x${rotatedBitmap.height}"
            )
            return rotatedBitmap
        } catch (e: Exception) {
            Log.e("@ROTATE", "Failed to rotate bitmap: ${e.message}")
            // If rotation fails, return original bitmap
            return bitmap
        }
    }

    fun orderPoints(points: Array<Point>): Array<Point> {
        // Sort by x + y for top-left, and x - y for top-right, etc.
        val sorted = points.sortedWith(compareBy({ it.y }, { it.x }))

        val topPoints = sorted.take(2).sortedBy { it.x } // top-left, top-right
        val bottomPoints = sorted.takeLast(2).sortedBy { it.x } // bottom-left, bottom-right

        val result = arrayOf(
            topPoints[0],   // top-left
            topPoints[1],   // top-right
            bottomPoints[1],// bottom-right
            bottomPoints[0] // bottom-left
        )

        Log.d("@ORDER", "Original: ${points.joinToString()}")
        Log.d("@ORDER", "Ordered: ${result.joinToString()}")

        return result
    }


    fun isCornerValid(corners: List<Point>, imageWidth: Int, imageHeight: Int): Boolean {
        if (corners.size != 4) return false

        val topWidth = euclideanDistance(corners[0], corners[1])
        val bottomWidth = euclideanDistance(corners[2], corners[3])
        val leftHeight = euclideanDistance(corners[0], corners[2])
        val rightHeight = euclideanDistance(corners[1], corners[3])

        val avgWidth = (topWidth + bottomWidth) / 2.0
        val avgHeight = (leftHeight + rightHeight) / 2.0
        val ratio = avgWidth / avgHeight

        val minWidthThreshold = imageWidth * 0.05
        val minHeightThreshold = imageHeight * 0.05

        val widthDiff = abs(topWidth - bottomWidth)
        val heightDiff = abs(leftHeight - rightHeight)
        // Increased side difference allowance for real-world NRC photos (15% instead of 2.5%)
        val maxWidthDiff = imageWidth * 0.15
        val maxHeightDiff = imageHeight * 0.15
        if (avgWidth < minWidthThreshold || avgHeight < minHeightThreshold) return false
        // More flexible aspect ratio for rotated cards (0.5 to 2.0 instead of 1.0 to 2.0)
        if (ratio < 0.5 || ratio > 2.0) return false
        if (widthDiff > maxWidthDiff || heightDiff > maxHeightDiff) return false
        return true
    }

    fun euclideanDistance(p1: Point, p2: Point): Double {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }

    /**
     * Convert an ImageProxy (YUV_420_888) to a Bitmap (ARGB_8888).
     */
    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer: ByteBuffer = image.planes[0].buffer
        val vuBuffer: ByteBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()
        val nv21 = ByteArray(ySize + vuSize)

        // Copy Y + VU into one NV21 byte array
        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        // Convert NV21 -> JPEG -> Bitmap
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, image.width, image.height),
            100,
            out
        )
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    /**
     * Convert a Bitmap (ARGB_8888) into an OpenCV Mat (CV_8UC4).
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    /**
     * Convert a Mat (CV_8UC1 or CV_8UC4) into a Bitmap (ARGB_8888).
     */
    @SuppressLint("UseKtx")
    fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }


}


