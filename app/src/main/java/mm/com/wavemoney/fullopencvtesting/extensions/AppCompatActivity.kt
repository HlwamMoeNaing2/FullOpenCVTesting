package mm.com.wavemoney.fullopencvtesting.extensions

import android.graphics.Rect
import android.os.Build
import androidx.appcompat.app.AppCompatActivity

@Suppress("DEPRECATION")
        /**
         * @property screenBounds the screen bounds (used to get screen width and height)
         */
val AppCompatActivity.screenBounds: Rect
    get() {
        // currentWindowMetrics was added in Android R
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return windowManager.currentWindowMetrics.bounds
        }

        // fall back to get screen width and height if using a version before Android R
        return Rect(
            0, 0, windowManager.defaultDisplay.width, windowManager.defaultDisplay.height
        )
    }

/**
 * @property screenWidth the screen width
 */
val AppCompatActivity.screenWidth: Int get() = screenBounds.width()

/**
 * @property screenHeight the screen height
 */
val AppCompatActivity.screenHeight: Int get() = screenBounds.height()