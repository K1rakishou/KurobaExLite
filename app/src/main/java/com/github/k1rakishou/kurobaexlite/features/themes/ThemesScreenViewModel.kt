package com.github.k1rakishou.kurobaexlite.features.themes

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewModelScope
import com.github.k1rakishou.kurobaexlite.base.BaseViewModel
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.themes.ChanTheme
import com.github.k1rakishou.kurobaexlite.themes.ThemeEngine
import com.github.k1rakishou.kurobaexlite.themes.ThemeStorage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

class ThemesScreenViewModel(
  private val themeStorage: ThemeStorage,
  private val themeEngine: ThemeEngine
) : BaseViewModel() {
  private val _themes = mutableStateOf<ImmutableList<ThemeUi>?>(null)
  val themes: State<ImmutableList<ThemeUi>?>
    get() = _themes

  init {
    viewModelScope.launch {
      reloadThemes()
    }
  }

  suspend fun exportThemeToFile(chanTheme: ChanTheme, fileUri: Uri): Result<Boolean> {
    return themeStorage.saveThemeToFile(chanTheme, fileUri)
  }

  suspend fun convertThemeToJsonString(chanTheme: ChanTheme): Result<String> {
    return themeStorage.themeToJsonString(chanTheme)
  }

  suspend fun importThemeFromFile(inputFileUri: Uri, themeFileName: String): Result<Boolean> {
    return Result.Try {
      val success = themeStorage.storeTheme(inputFileUri, themeFileName).unwrap()
      if (!success) {
        return@Try false
      }

      themeEngine.switchToTheme(themeFileName)
      reloadThemes()
      return@Try true
    }
  }

  suspend fun importThemeFromClipboard(themeJson: String, themeFileName: String): Result<Boolean> {
    return Result.Try {
      val success = themeStorage.storeTheme(themeJson, themeFileName).unwrap()
      if (!success) {
        return@Try false
      }

      themeEngine.switchToTheme(themeFileName)
      reloadThemes()
      return@Try true
    }
  }

  suspend fun deleteTheme(nameOnDisk: String): Result<Boolean> {
    return Result.Try {
      val isDarkTheme = themeStorage.getTheme(nameOnDisk)?.isDarkTheme
        ?: return@Try false

      val success = themeStorage.deleteTheme(nameOnDisk).unwrap()
      if (!success) {
        return@Try false
      }

      themeEngine.switchToDefaultTheme(isDarkTheme)
      reloadThemes()
      return@Try true
    }
  }

  fun isDefaultTheme(nameOnDisk: String): Boolean {
    return themeEngine.isDefaultTheme(nameOnDisk)
  }

  fun switchToTheme(nameOnDisk: String) {
    themeEngine.switchToTheme(nameOnDisk)
  }

  suspend fun themeWithNameAlreadyExists(themeFileName: String): Result<Boolean> {
    return themeStorage.themeWithNameExists(themeFileName)
  }

  private suspend fun reloadThemes() {
    _themes.value = themeStorage.loadAllThemes()
      .entries
      .map { (nameOnDisk, chanTheme) -> ThemeUi(nameOnDisk, chanTheme) }
      .toImmutableList()
  }

  @Stable
  data class ThemeUi(
    val nameOnDisk: String,
    val chanTheme: ChanTheme
  )

}