package mm.com.wavemoney.fullopencvtesting.utils
/*

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Simple image processor that doesn't require OpenCV
 * Uses pure Java/Kotlin for basic image processing operations
 */
class SimpleImageProcessor {

    data class Point(val x: Double, val y: Double)
    data class Rect(val x: Int, val y: Int, val width: Int, val height: Int)

    /**
     * Convert bitmap to grayscale
     */
    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Convert to grayscale using luminance formula
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                grayBitmap.setPixel(x, y, Color.rgb(gray, gray, gray))
            }
        }

        return grayBitmap
    }

    /**
     * Apply Gaussian blur (simplified version)
     */
    fun gaussianBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val blurredBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                var rSum = 0
                var gSum = 0
                var bSum = 0
                var count = 0

                // Simple box blur (not true Gaussian)
                for (dx in -radius..radius) {
                    for (dy in -radius..radius) {
                        val nx = x + dx
                        val ny = y + dy

                        if (nx in 0 until width && ny in 0 until height) {
                            val pixel = bitmap.getPixel(nx, ny)
                            rSum += Color.red(pixel)
                            gSum += Color.green(pixel)
                            bSum += Color.blue(pixel)
                            count++
                        }
                    }
                }

                if (count > 0) {
                    val r = rSum / count
                    val g = gSum / count
                    val b = bSum / count
                    blurredBitmap.setPixel(x, y, Color.rgb(r, g, b))
                }
            }
        }

        return blurredBitmap
    }

    /**
     * Edge detection using Sobel operator
     */
    fun detectEdges(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val edgeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Sobel kernels
        val sobelX = intArrayOf(-1, 0, 1, -2, 0, 2, -1, 0, 1)
        val sobelY = intArrayOf(-1, -2, -1, 0, 0, 0, 1, 2, 1)

        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                var gx = 0
                var gy = 0

                // Apply Sobel operators
                for (i in 0..2) {
                    for (j in 0..2) {
                        val pixel = bitmap.getPixel(x + i - 1, y + j - 1)
                        val gray = Color.red(pixel) // Assuming grayscale
                        gx += gray * sobelX[i * 3 + j]
                        gy += gray * sobelY[i * 3 + j]
                    }
                }

                val magnitude = sqrt((gx * gx + gy * gy).toDouble()).toInt()
                val threshold = 50
                val edgeValue = if (magnitude > threshold) 255 else 0
                edgeBitmap.setPixel(x, y, Color.rgb(edgeValue, edgeValue, edgeValue))
            }
        }

        return edgeBitmap
    }

    /**
     * Find contours in binary image
     */
    fun findContours(bitmap: Bitmap): List<List<Point>> {
        val width = bitmap.width
        val height = bitmap.height
        val visited = Array(height) { BooleanArray(width) }
        val contours = mutableListOf<List<Point>>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                if (!visited[y][x] && isEdgePixel(bitmap, x, y)) {
                    val contour = traceContour(bitmap, visited, x, y)
                    if (contour.size > 10) { // Minimum contour size
                        contours.add(contour)
                    }
                }
            }
        }

        return contours
    }

    private fun isEdgePixel(bitmap: Bitmap, x: Int, y: Int): Boolean {
        if (x < 0 || x >= bitmap.width || y < 0 || y >= bitmap.height) return false
        val pixel = bitmap.getPixel(x, y)
        return Color.red(pixel) > 128 // Threshold for edge detection
    }

    private fun traceContour(bitmap: Bitmap, visited: Array<BooleanArray>, startX: Int, startY: Int): List<Point> {
        val contour = mutableListOf<Point>()
        val directions = arrayOf(
            intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, 0), intArrayOf(1, -1),
            intArrayOf(0, -1), intArrayOf(-1, -1), intArrayOf(-1, 0), intArrayOf(-1, 1)
        )

        var x = startX
        var y = startY
        var dir = 0

        do {
            visited[y][x] = true
            contour.add(Point(x.toDouble(), y.toDouble()))

            // Find next edge pixel
            var found = false
            for (i in 0 until 8) {
                val nextDir = (dir + i) % 8
                val nx = x + directions[nextDir][0]
                val ny = y + directions[nextDir][1]

                if (isEdgePixel(bitmap, nx, ny) && !visited[ny][nx]) {
                    x = nx
                    y = ny
                    dir = nextDir
                    found = true
                    break
                }
            }

            if (!found) break
        } while (x != startX || y != startY)

        return contour
    }

    /**
     * Approximate polygon from contour
     */
    fun approximatePolygon(contour: List<Point>, epsilon: Double): List<Point> {
        if (contour.size < 3) return contour

        // Douglas-Peucker algorithm (simplified)
        val result = mutableListOf<Point>()
        result.add(contour.first())

        var maxDistance = 0.0
        var maxIndex = 0

        for (i in 1 until contour.size - 1) {
            val distance = pointToLineDistance(contour[i], contour.first(), contour.last())
            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = i
            }
        }

        if (maxDistance > epsilon) {
            val firstHalf = approximatePolygon(contour.subList(0, maxIndex + 1), epsilon)
            val secondHalf = approximatePolygon(contour.subList(maxIndex, contour.size), epsilon)
            result.clear()
            result.addAll(firstHalf.dropLast(1))
            result.addAll(secondHalf)
        } else {
            result.add(contour.last())
        }

        return result
    }

    private fun pointToLineDistance(point: Point, lineStart: Point, lineEnd: Point): Double {
        val A = point.x - lineStart.x
        val B = point.y - lineStart.y
        val C = lineEnd.x - lineStart.x
        val D = lineEnd.y - lineStart.y

        val dot = A * C + B * D
        val lenSq = C * C + D * D

        if (lenSq == 0.0) return sqrt(A * A + B * B)

        val param = dot / lenSq

        val xx: Double
        val yy: Double

        if (param < 0) {
            xx = lineStart.x
            yy = lineStart.y
        } else if (param > 1) {
            xx = lineEnd.x
            yy = lineEnd.y
        } else {
            xx = lineStart.x + param * C
            yy = lineStart.y + param * D
        }

        val dx = point.x - xx
        val dy = point.y - yy
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculate contour area
     */
    fun contourArea(contour: List<Point>): Double {
        if (contour.size < 3) return 0.0

        var area = 0.0
        for (i in contour.indices) {
            val j = (i + 1) % contour.size
            area += contour[i].x * contour[j].y
            area -= contour[j].x * contour[i].y
        }

        return abs(area) / 2.0
    }

    /**
     * Get bounding rectangle of contour
     */
    fun boundingRect(contour: List<Point>): Rect {
        if (contour.isEmpty()) return Rect(0, 0, 0, 0)

        var minX = contour[0].x
        var maxX = contour[0].x
        var minY = contour[0].y
        var maxY = contour[0].y

        for (point in contour) {
            minX = min(minX, point.x)
            maxX = max(maxX, point.x)
            minY = min(minY, point.y)
            maxY = max(maxY, point.y)
        }

        return Rect(
            minX.toInt(),
            minY.toInt(),
            (maxX - minX).toInt(),
            (maxY - minY).toInt()
        )
    }

    /**
     * Rotate bitmap
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Crop bitmap using perspective transform
     */
    fun cropPerspective(bitmap: Bitmap, corners: List<Point>): Bitmap {
        if (corners.size != 4) return bitmap

        // Calculate destination rectangle
        val width = max(
            distance(corners[0], corners[1]),
            distance(corners[2], corners[3])
        ).toInt()
        val height = max(
            distance(corners[0], corners[2]),
            distance(corners[1], corners[3])
        ).toInt()

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        // Simple perspective transform (not perfect but functional)
        val path = Path()
        path.moveTo(corners[0].x.toFloat(), corners[0].y.toFloat())
        path.lineTo(corners[1].x.toFloat(), corners[1].y.toFloat())
        path.lineTo(corners[2].x.toFloat(), corners[2].y.toFloat())
        path.lineTo(corners[3].x.toFloat(), corners[3].y.toFloat())
        path.close()

        canvas.clipPath(path)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    private fun distance(p1: Point, p2: Point): Double {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }
}
 */