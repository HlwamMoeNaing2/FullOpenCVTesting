package mm.com.wavemoney.fullopencvtesting.utils

import android.content.Context
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment

fun Fragment.updateStatusBarColor(@ColorInt color: Int) {
    val window = requireActivity().window
    window.statusBarColor = color
    WindowInsetsControllerCompat(window, window.decorView)
        .isAppearanceLightStatusBars = ColorUtils.calculateLuminance(color) > 0.5
}
fun Fragment.setSystemNavigationBarColor(@ColorRes color: Int) {
    requireActivity().window.navigationBarColor = ContextCompat.getColor(requireContext(), color)
}

fun AppCompatImageView.setIcon(@DrawableRes iconRes: Int, context: Context) {
    val drawable = ContextCompat.getDrawable(context, iconRes)
    this.setImageDrawable(drawable)
}