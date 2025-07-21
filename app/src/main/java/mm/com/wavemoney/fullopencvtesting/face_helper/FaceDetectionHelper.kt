package mm.com.wavemoney.fullopencvtesting.face_helper


import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfRect
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier

import java.io.ByteArrayOutputStream


class FaceDetectionHelper {
    fun detectFaces(
        bitmap: Bitmap, cascadeClassifier: CascadeClassifier?
    ): FaceResult {
        val mat = Mat()
        val grayMat = Mat()
        val faces = MatOfRect()
        return try {
            Utils.bitmapToMat(bitmap, mat)
            Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGBA2GRAY)
            try {
                cascadeClassifier?.detectMultiScale(grayMat, faces)
            } catch (e: Exception) {

                return FaceResult.NO_FACE
            }
            when (faces.toArray().size) {
                1 -> FaceResult.SINGLE
                in 2..Int.MAX_VALUE -> FaceResult.MULTIPLE
                else -> FaceResult.NO_FACE
            }
        } finally {
            faces.release()
            grayMat.release()
            mat.release()
        }
    }


    fun processImage(image: ImageProxy): Bitmap {
        try {
            // YUV to NV21 and convert to bitmap
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
            val bytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            return rotateBitmap(bitmap, image.imageInfo.rotationDegrees)

        } catch (t: Throwable) {

            throw t // this will rethrow, and finally will still run
        } finally {
            image.close() // this is reachable and always runs, even after throw
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegree: Int): Bitmap {
        if (rotationDegree == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotationDegree.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

enum class FaceResult {
    SINGLE, MULTIPLE, NO_FACE
}

