package com.github.k1rakishou.kurobaexlite.themes
import android.graphics.Color
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor


class ThemeEngine {
  val chanTheme: ChanTheme = DefaultDarkTheme()

  private val listeners = hashMapOf<Long, ThemeChangesListener>()

  fun addListener(key: Long, listener: ThemeChangesListener) {
    listeners[key] = listener
  }

  fun removeListener(key: Long) {
    listeners.remove(key)
  }

  fun addListener(listener: ThemeChangesListener) {
    listeners[listener.hashCode().toLong()] = listener
  }

  fun removeListener(listener: ThemeChangesListener) {
    listeners.remove(listener.hashCode().toLong())
  }

  fun notifyListeners() {
    listeners.forEach { listener -> listener.value.onThemeChanged() }
  }

  interface ThemeChangesListener {
    fun onThemeChanged()
  }

  companion object {
    private const val TAG = "ThemeEngine"

    val LIGHT_DRAWABLE_TINT_COMPOSE = ComposeColor(Color.parseColor("#EEEEEE"))
    val DARK_DRAWABLE_TINT_COMPOSE = ComposeColor(Color.parseColor("#7E7E7E"))

    @JvmStatic
    fun resolveDrawableTintColor(chanTheme: ChanTheme): ComposeColor {
      return if (chanTheme.isBackColorDark) {
        LIGHT_DRAWABLE_TINT_COMPOSE
      } else {
        DARK_DRAWABLE_TINT_COMPOSE
      }
    }

    fun resolveDrawableTintColor(isCurrentColorDark: Boolean): ComposeColor {
      return if (isCurrentColorDark) {
        LIGHT_DRAWABLE_TINT_COMPOSE
      } else {
        DARK_DRAWABLE_TINT_COMPOSE
      }
    }

    /**
     * Makes color brighter if factor > 1.0f or darker if factor < 1.0f
     */
    @JvmStatic
    fun manipulateColor(color: Int, factor: Float): Int {
      val a = Color.alpha(color)
      val r = (Color.red(color) * factor).roundToInt()
      val g = (Color.green(color) * factor).roundToInt()
      val b = (Color.blue(color) * factor).roundToInt()
      return Color.argb(a, min(r, 255), min(g, 255), min(b, 255))
    }

    @JvmStatic
    fun manipulateColor(color: ComposeColor, factor: Float): ComposeColor {
      return ComposeColor(manipulateColor(color.toArgb(), factor))
    }

    @JvmStatic
    fun getComplementaryColor(color: Int): Int {
      return Color.rgb(255 - Color.red(color), 255 - Color.green(color), 255 - Color.blue(color))
    }

    @JvmStatic
    fun updateAlphaForColor(color: Int, @FloatRange(from = 0.0, to = 1.0) newAlpha: Float): Int {
      return ColorUtils.setAlphaComponent(color, (newAlpha * 255f).toInt())
    }

    @JvmStatic
    fun isDarkColor(color: ULong): Boolean {
      return isDarkColor(color.toInt())
    }

    @JvmStatic
    fun isDarkColor(color: ComposeColor): Boolean {
      return isDarkColor(color.toArgb())
    }

    @JvmStatic
    fun isDarkColor(color: Int): Boolean {
      return ColorUtils.calculateLuminance(color) < 0.5f
    }

    @JvmStatic
    fun isNearToFullyBlackColor(color: Int): Boolean {
      return ColorUtils.calculateLuminance(color) < 0.01f
    }

    private val array = FloatArray(3)

    @JvmStatic
    @Synchronized
    fun colorToHsl(color: Int): HSL {
      ColorUtils.colorToHSL(color, array)

      return HSL(
        hue = array[0],
        saturation = array[1],
        lightness = array[2]
      )
    }

    @JvmStatic
    @Synchronized
    fun hslToColor(hsl: HSL): Int {
      array[0] = hsl.hue
      array[1] = hsl.saturation
      array[2] = hsl.lightness

      return ColorUtils.HSLToColor(array)
    }

  }
}