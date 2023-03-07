package com.github.k1rakishou.kurobaexlite.ui.themes
import android.graphics.Color
import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.ui.themes.def.Kuroneko
import com.github.k1rakishou.kurobaexlite.ui.themes.def.Shironeko
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color as ComposeColor


class ThemeEngine(
  private val appScope: CoroutineScope,
  private val appSettings: AppSettings,
  private val themeStorage: ThemeStorage
) : IThemeEngine {
  val defaultLightTheme = Shironeko()
  val defaultDarkTheme = Kuroneko()

  @Volatile
  private var _chanTheme: ChanTheme = defaultDarkTheme
  override val chanTheme: ChanTheme
    get() = _chanTheme

  private val listeners = hashMapOf<Long, ThemeChangesListener>()

  fun init() {
    appScope.launch {
      themeStorage.loadAllThemes()
      updateThemeAndNotifyListeners()
    }
  }

  override fun toggleTheme() {
    appScope.launch {
      appSettings.isDarkThemeUsed.toggle()
      updateThemeAndNotifyListeners()
    }
  }

  override fun switchToDefaultTheme(darkThemeWasUsed: Boolean) {
    appScope.launch {
      appSettings.isDarkThemeUsed.write(darkThemeWasUsed)

      if (darkThemeWasUsed) {
        appSettings.currentDarkThemeName.write(defaultDarkTheme.name)
      } else {
        appSettings.currentLightThemeName.write(defaultLightTheme.name)
      }

      updateThemeAndNotifyListeners()
    }
  }

  override fun switchToTheme(nameOnDisk: String) {
    appScope.launch {
      val theme = themeStorage.parseThemeByFileName(nameOnDisk)
        .getOrNull()
        ?: return@launch

      appSettings.isDarkThemeUsed.write(theme.isDarkTheme)

      if (theme.isDarkTheme) {
        appSettings.currentDarkThemeName.write(nameOnDisk)
      } else {
        appSettings.currentLightThemeName.write(nameOnDisk)
      }

      updateThemeAndNotifyListeners()
    }
  }

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

  fun notifyListeners(chanTheme: ChanTheme) {
    listeners.forEach { listener -> listener.value.onThemeChanged(chanTheme) }
  }

  suspend fun currentThemeName(): String {
    val isDarkThemeUsed = appSettings.isDarkThemeUsed.read()

    val currentThemeName = if (isDarkThemeUsed) {
      appSettings.currentDarkThemeName.read()
    } else {
      appSettings.currentLightThemeName.read()
    }

    if (currentThemeName.isNotBlank()) {
      return currentThemeName
    }

    return if (isDarkThemeUsed) {
      defaultDarkTheme.name
    } else {
      defaultLightTheme.name
    }
  }

  private suspend fun updateThemeAndNotifyListeners() {
    val isDarkThemeUsed = appSettings.isDarkThemeUsed.read()

    _chanTheme = if (isDarkThemeUsed) {
      val nameOnDisk = appSettings.currentDarkThemeName.read()
      themeByThemeNameOrDefault(nameOnDisk, true)
    } else {
      val nameOnDisk = appSettings.currentLightThemeName.read()
      themeByThemeNameOrDefault(nameOnDisk, false)
    }

    notifyListeners(_chanTheme)
  }

  private suspend fun themeByThemeNameOrDefault(nameOnDisk: String, isDarkThemeUsed: Boolean): ChanTheme {
    if (nameOnDisk == defaultDarkTheme.name) {
      return defaultDarkTheme
    }

    if (nameOnDisk == defaultLightTheme.name) {
      return defaultLightTheme
    }

    val chanTheme = themeStorage.getTheme(nameOnDisk)
    if (chanTheme != null) {
      return chanTheme
    }

    if (isDarkThemeUsed) {
      return defaultDarkTheme
    }

    return defaultLightTheme
  }

  fun isDefaultTheme(nameOnDisk: String): Boolean {
    return nameOnDisk == defaultLightTheme.name || nameOnDisk == defaultDarkTheme.name
  }

  interface ThemeChangesListener {
    fun onThemeChanged(newChanTheme: ChanTheme)
  }

  companion object {
    private const val TAG = "ThemeEngine"

    private val LIGHT_COLOR_COMPOSE = ComposeColor(Color.parseColor("#EEEEEE"))
    private val DARK_COLOR_COMPOSE = ComposeColor(Color.parseColor("#5E5E5E"))

    @JvmStatic
    fun resolveDarkOrLightColor(chanTheme: ChanTheme): ComposeColor {
      return if (chanTheme.isBackColorDark) {
        LIGHT_COLOR_COMPOSE
      } else {
        DARK_COLOR_COMPOSE
      }
    }

    fun resolveDarkOrLightColor(isCurrentColorDark: Boolean): ComposeColor {
      return if (isCurrentColorDark) {
        LIGHT_COLOR_COMPOSE
      } else {
        DARK_COLOR_COMPOSE
      }
    }

    fun resolveDarkOrLightColor(color: ComposeColor): ComposeColor {
      return if (isDarkColor(color)) {
        LIGHT_COLOR_COMPOSE
      } else {
        DARK_COLOR_COMPOSE
      }
    }

    fun resolveFabContentColor(color: ComposeColor): ComposeColor {
      return if (isDarkColor(color)) {
        ComposeColor.White
      } else {
        ComposeColor.Black
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