package com.github.k1rakishou.kurobaexlite.ui.themes

import android.content.Context
import android.net.Uri
import androidx.annotation.GuardedBy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.github.k1rakishou.fsaf.FileManager
import com.github.k1rakishou.kurobaexlite.helpers.util.Try
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.removeExtensionFromFileName
import com.github.k1rakishou.kurobaexlite.helpers.util.unwrap
import com.github.k1rakishou.kurobaexlite.helpers.util.useBufferedSink
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.ui.themes.def.Kuroneko
import com.github.k1rakishou.kurobaexlite.ui.themes.def.Shironeko
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import android.graphics.Color as AndroidColor

class ThemeStorage(
  private val appContext: Context,
  private val moshi: Moshi,
  private val fileManager: FileManager
) {
  private val mutex = Mutex()

  @GuardedBy("mutex")
  private val themesCached = mutableMapOf<String, ChanTheme>()

  private val themesDirectory by lazy {
    File(appContext.filesDir, "themes").also { directory ->
      if (!directory.exists()) {
        directory.mkdir()
      }
    }
  }

  private val kuroneko = Kuroneko()
  private val shironeko = Shironeko()

  suspend fun loadAllThemes(): Map<String, ChanTheme> {
    return mutex.withLock {
      themesCached.clear()

      themesCached[kuroneko.name] = kuroneko
      themesCached[shironeko.name] = shironeko

      val loadedThemes = withContext(Dispatchers.IO) {
        themesDirectory.listFiles()?.mapNotNull { themeFile ->
          val chanTheme = parseThemeByFileName(themeFileName = themeFile.name)
            .getOrNull()
            ?: return@mapNotNull null

          return@mapNotNull themeFile.name to chanTheme
        }
      } ?: emptyList()

      loadedThemes.forEach { (themeFileName, chanTheme) ->
        themesCached[themeFileName] = chanTheme
      }

      return@withLock themesCached.toMap()
    }
  }

  suspend fun getTheme(nameOnDisk: String): ChanTheme? {
    return mutex.withLock { themesCached[nameOnDisk] }
  }

  suspend fun deleteTheme(nameOnDisk: String): Result<Boolean> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val fileToDelete = File(themesDirectory, nameOnDisk)
        return@Try fileToDelete.delete()
      }
    }
  }

  suspend fun storeTheme(inputFileUri: Uri, themeFileName: String): Result<Boolean> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val chanTheme = readThemeFromFile(inputFileUri).unwrap()

        val themeJson = themeToJsonString(chanTheme).unwrap()
        if (themeJson.isEmpty()) {
          logcatError(TAG) { "storeTheme() failed to convert chanTheme into json" }
          throw ChanThemeException("Failed to convert chanTheme into json")
        }

        return@withContext storeTheme(themeJson, themeFileName)
      }
    }
  }

  suspend fun storeTheme(themeJson: String, themeFileName: String): Result<Boolean> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        parseThemeJson(themeJson).unwrap()

        val themeFile = File(themesDirectory, themeFileName)
        if (themeFile.exists()) {
          if (!themeFile.delete()) {
            logcatError(TAG) { "storeTheme() failed to delete existing themeFile: ${themeFile.absolutePath}" }
            throw ChanThemeException("Failed to delete existing themeFile: ${themeFile.absolutePath}")
          }
        }

        if (!themeFile.createNewFile()) {
          logcatError(TAG) { "storeTheme() failed to create new themeFile: ${themeFile.absolutePath}" }
          throw ChanThemeException("Failed to create new themeFile: ${themeFile.absolutePath}")
        }

        themeFile.outputStream().useBufferedSink { bufferedSink ->
          bufferedSink.write(themeJson.encodeUtf8())
        }

        return@Try true
      }
    }
  }

  suspend fun parseThemeByFileName(themeFileName: String): Result<ChanTheme> {
    return Result.Try {
      if (kuroneko.name.equals(themeFileName, ignoreCase = true)) {
        return@Try kuroneko
      }

      if (shironeko.name.equals(themeFileName, ignoreCase = true)) {
        return@Try shironeko
      }

      return withContext(Dispatchers.IO) {
        val themeFile = File(themesDirectory, themeFileName)
        if (!themeFile.exists()) {
          logcatError(TAG) { "parseThemeByFileName() themeFile does not exist (${themeFile.absolutePath})" }
          throw ChanThemeException("Theme file does not exist (${themeFile.absolutePath})")
        }

        val themeJson = themeFile.readText()
        return@withContext parseThemeJson(themeJson)
      }
    }
  }

  suspend fun parseThemeJson(themeJson: String): Result<ChanTheme> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val chanThemeJson = try {
          moshi.adapter<ChanThemeJson>(ChanThemeJson::class.java).fromJson(themeJson)
        } catch (error: Throwable) {
          logcatError(TAG) { "parseThemeByFileName() error while parsing ChanThemeJson: ${error.asLogIfImportantOrErrorMessage()}" }
          throw ChanThemeException("Error while parsing ChanThemeJson: ${error.asLogIfImportantOrErrorMessage()}", error)
        }

        if (chanThemeJson == null) {
          logcatError(TAG) { "parseThemeByFileName() failed to parse chanThemeJson" }
          throw ChanThemeException("Failed to parse chanThemeJson")
        }

        val invalidFields = chanThemeJson.collectInvalidFields()
        if (invalidFields.isNotEmpty()) {
          logcatError(TAG) { "parseThemeByFileName() failed to parse fields: \'${invalidFields.joinToString()}\'" }
          throw ChanThemeException("Failed to parse fields or fields not found in json: '${invalidFields.joinToString()}'")
        }

        val malformedFields = chanThemeJson.collectMalformedFields()
        if (malformedFields.isNotEmpty()) {
          logcatError(TAG) { "parseThemeByFileName() malformed fields detected: \'${malformedFields.joinToString()}\'" }
          throw ChanThemeException("Malformed fields detected: '${malformedFields.joinToString()}'")
        }

        return@withContext chanThemeJson.toChanTheme()
      }
    }
  }

  suspend fun readThemeFromFile(inputFileUri: Uri): Result<ChanTheme> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val externalFile = fileManager.fromUri(inputFileUri)
        if (externalFile == null) {
          logcatError(TAG) { "readThemeFromFile() externalFile is null: \'${inputFileUri}\'" }
          throw ChanThemeException("File is null: '${inputFileUri}'")
        }

        val themeJson = fileManager.getInputStream(externalFile)?.use { inputStream ->
          String(inputStream.readBytes())
        }

        if (themeJson.isNullOrEmpty()) {
          logcatError(TAG) { "readThemeFromFile() failed to read externalFile: \'${inputFileUri}\'" }
          throw ChanThemeException("Failed to read externalFile: '${inputFileUri}'")
        }

        return@Try parseThemeJson(themeJson).unwrap()
      }
    }
  }

  suspend fun saveThemeToFile(chanTheme: ChanTheme, outputFileUri: Uri): Result<Boolean> {
    return withContext(Dispatchers.IO) {
      return@withContext Result.Try {
        val json = themeToJsonString(chanTheme).unwrap()

        val externalFile = fileManager.fromUri(outputFileUri)
        if (externalFile == null) {
          logcatError(TAG) { "saveThemeToFile() externalFile is null: \'${outputFileUri}\'" }
          return@Try false
        }

        fileManager.getOutputStream(externalFile)?.use { outputStream ->
          outputStream.useBufferedSink { bufferedSink -> bufferedSink.write(json.encodeUtf8()) }
        }

        return@Try true
      }
    }
  }

  fun themeToJsonString(chanTheme: ChanTheme): Result<String> {
    return Result.Try {
      val chanThemeJson = ChanThemeJson.fromChanTheme(chanTheme)
      return@Try moshi.adapter(ChanThemeJson::class.java)
        .toJson(chanThemeJson)
    }
  }

  suspend fun themeWithNameExists(themeFileName: String): Result<Boolean> {
    return Result.Try {
      val themeFileNameWithoutExtension = themeFileName.removeExtensionFromFileName()

      if (kuroneko.name.equals(themeFileNameWithoutExtension, ignoreCase = true)) {
        return@Try true
      }

      if (shironeko.name.equals(themeFileNameWithoutExtension, ignoreCase = true)) {
        return@Try true
      }

      val actualThemeFileName = "${themeFileNameWithoutExtension}.json"

      return@Try withContext(Dispatchers.IO) {
        val files = themesDirectory.listFiles() ?: emptyArray()
        if (files.isEmpty()) {
          return@withContext false
        }

        return@withContext files.any { file -> file.name.equals(actualThemeFileName, ignoreCase = true) }
      }
    }
  }

  @JsonClass(generateAdapter = true)
  data class ChanThemeJson(
    @Json(name = "name") val name: String?,
    @Json(name = "is_light_theme") val isLightTheme: Boolean?,
    @Json(name = "light_status_bar") val lightStatusBar: Boolean?,
    @Json(name = "light_nav_bar") val lightNavBar: Boolean?,
    @Json(name = "accent_color") val accentColor: String?,
    @Json(name = "gradient_top_color") val gradientTopColor: String?,
    @Json(name = "gradient_bottom_color") val gradientBottomColor: String?,
    @Json(name = "behind_gradient_color") val behindGradientColor: String?,
    @Json(name = "back_color") val backColor: String?,
    @Json(name = "back_color_secondary") val backColorSecondary: String?,
    @Json(name = "selected_on_back_color") val selectedOnBackColor: String?,
    @Json(name = "highlighted_on_back_color") val highlightedOnBackColor: String?,
    @Json(name = "error_color") val errorColor: String?,
    @Json(name = "text_color_primary") val textColorPrimary: String?,
    @Json(name = "text_color_secondary") val textColorSecondary: String?,
    @Json(name = "text_color_hint") val textColorHint: String?,
    @Json(name = "post_saved_reply_color") val postSavedReplyColor: String?,
    @Json(name = "post_subject_color") val postSubjectColor: String?,
    @Json(name = "post_details_color") val postDetailsColor: String?,
    @Json(name = "post_name_color") val postNameColor: String?,
    @Json(name = "post_inline_quote_color") val postInlineQuoteColor: String?,
    @Json(name = "post_quote_color") val postQuoteColor: String?,
    @Json(name = "post_link_color") val postLinkColor: String?,
    @Json(name = "post_spoiler_color") val postSpoilerColor: String?,
    @Json(name = "post_spoiler_reveal_text_color") val postSpoilerRevealTextColor: String?,
    @Json(name = "divider_color") val dividerColor: String?,
    @Json(name = "scrollbar_track_color") val scrollbarTrackColor: String?,
    @Json(name = "scrollbar_thumb_color_normal") val scrollbarThumbColorNormal: String?,
    @Json(name = "scrollbar_thumb_color_dragged") val scrollbarThumbColorDragged: String?,
    @Json(name = "bookmark_counter_has_replies_color") val bookmarkCounterHasRepliesColor: String?,
    @Json(name = "bookmark_counter_normal_color") val bookmarkCounterNormalColor: String?
  ) {

    fun collectInvalidFields(): List<String> {
      val invalidFields = mutableListOf<String>()

      if (name.isNullOrEmpty()) invalidFields += "name"
      if (isLightTheme == null) invalidFields += "is_light_theme"
      if (lightStatusBar == null) invalidFields += "light_status_bar"
      if (lightNavBar == null) invalidFields += "light_nav_bar"
      if (accentColor == null) invalidFields += "accent_color"
      if (gradientTopColor == null) invalidFields += "gradient_top_color"
      if (gradientBottomColor == null) invalidFields += "gradient_bottom_color"
      if (behindGradientColor == null) invalidFields += "behind_gradient_color"
      if (backColor == null) invalidFields += "back_color"
      if (backColorSecondary == null) invalidFields += "back_color_secondary"
      if (selectedOnBackColor == null) invalidFields += "selected_on_back_color"
      if (highlightedOnBackColor == null) invalidFields += "highlighted_on_back_color"
      if (errorColor == null) invalidFields += "error_color"
      if (textColorPrimary == null) invalidFields += "text_color_primary"
      if (textColorSecondary == null) invalidFields += "text_color_secondary"
      if (textColorHint == null) invalidFields += "text_color_hint"
      if (postSavedReplyColor == null) invalidFields += "post_saved_reply_color"
      if (postSubjectColor == null) invalidFields += "post_subject_color"
      if (postDetailsColor == null) invalidFields += "post_details_color"
      if (postNameColor == null) invalidFields += "post_name_color"
      if (postInlineQuoteColor == null) invalidFields += "post_inline_quote_color"
      if (postQuoteColor == null) invalidFields += "post_quote_color"
      if (postLinkColor == null) invalidFields += "post_link_color"
      if (postSpoilerColor == null) invalidFields += "post_spoiler_color"
      if (postSpoilerRevealTextColor == null) invalidFields += "post_spoiler_reveal_text_color"
      if (dividerColor == null) invalidFields += "divider_color"
      if (scrollbarTrackColor == null) invalidFields += "scrollbar_track_color"
      if (scrollbarThumbColorNormal == null) invalidFields += "scrollbar_thumb_color_normal"
      if (scrollbarThumbColorDragged == null) invalidFields += "scrollbar_thumb_color_dragged"
      if (bookmarkCounterHasRepliesColor == null) invalidFields += "bookmark_counter_has_replies_color"
      if (bookmarkCounterNormalColor == null) invalidFields += "bookmark_counter_normal_color"

      return invalidFields
    }

    fun collectMalformedFields(): List<String> {
      val malformedFields = mutableListOf<String>()

      if (accentColor.toColorOrNull() == null) malformedFields += "accent_color"
      if (gradientTopColor.toColorOrNull() == null) malformedFields += "gradient_top_color"
      if (gradientBottomColor.toColorOrNull() == null) malformedFields += "gradient_bottom_color"
      if (behindGradientColor.toColorOrNull() == null) malformedFields += "behind_gradient_color"
      if (backColor.toColorOrNull() == null) malformedFields += "back_color"
      if (backColorSecondary.toColorOrNull() == null) malformedFields += "back_color_secondary"
      if (selectedOnBackColor.toColorOrNull() == null) malformedFields += "selected_on_back_color"
      if (highlightedOnBackColor.toColorOrNull() == null) malformedFields += "highlighted_on_back_color"
      if (errorColor.toColorOrNull() == null) malformedFields += "error_color"
      if (textColorPrimary.toColorOrNull() == null) malformedFields += "text_color_primary"
      if (textColorSecondary.toColorOrNull() == null) malformedFields += "text_color_secondary"
      if (textColorHint.toColorOrNull() == null) malformedFields += "text_color_hint"
      if (postSavedReplyColor.toColorOrNull() == null) malformedFields += "post_saved_reply_color"
      if (postSubjectColor.toColorOrNull() == null) malformedFields += "post_subject_color"
      if (postDetailsColor.toColorOrNull() == null) malformedFields += "post_details_color"
      if (postNameColor.toColorOrNull() == null) malformedFields += "post_name_color"
      if (postInlineQuoteColor.toColorOrNull() == null) malformedFields += "post_inline_quote_color"
      if (postQuoteColor.toColorOrNull() == null) malformedFields += "post_quote_color"
      if (postLinkColor.toColorOrNull() == null) malformedFields += "post_link_color"
      if (postSpoilerColor.toColorOrNull() == null) malformedFields += "post_spoiler_color"
      if (postSpoilerRevealTextColor.toColorOrNull() == null) malformedFields += "post_spoiler_reveal_text_color"
      if (dividerColor.toColorOrNull() == null) malformedFields += "divider_color"
      if (scrollbarTrackColor.toColorOrNull() == null) malformedFields += "scrollbar_track_color"
      if (scrollbarThumbColorNormal.toColorOrNull() == null) malformedFields += "scrollbar_thumb_color_normal"
      if (scrollbarThumbColorDragged.toColorOrNull() == null) malformedFields += "scrollbar_thumb_color_dragged"
      if (bookmarkCounterHasRepliesColor.toColorOrNull() == null) malformedFields += "bookmark_counter_has_replies_color"
      if (bookmarkCounterNormalColor.toColorOrNull() == null) malformedFields += "bookmark_counter_normal_color"

      return malformedFields
    }

    fun toChanTheme(): Result<ChanTheme> {
      return Result.Try {
        return@Try ParsedChanTheme(
          name = name!!,
          isLightTheme = isLightTheme!!,
          lightStatusBar = lightStatusBar!!,
          lightNavBar = lightNavBar!!,
          accentColor = accentColor.toColor(),
          gradientTopColor = gradientTopColor.toColor(),
          gradientBottomColor = gradientBottomColor.toColor(),
          behindGradientColor = behindGradientColor.toColor(),
          backColor = backColor.toColor(),
          backColorSecondary = backColorSecondary.toColor(),
          selectedOnBackColor = selectedOnBackColor.toColor(),
          highlightedOnBackColor = highlightedOnBackColor.toColor(),
          errorColor = errorColor.toColor(),
          textColorPrimary = textColorPrimary.toColor(),
          textColorSecondary = textColorSecondary.toColor(),
          textColorHint = textColorHint.toColor(),
          postSavedReplyColor = postSavedReplyColor.toColor(),
          postSubjectColor = postSubjectColor.toColor(),
          postDetailsColor = postDetailsColor.toColor(),
          postNameColor = postNameColor.toColor(),
          postInlineQuoteColor = postInlineQuoteColor.toColor(),
          postQuoteColor = postQuoteColor.toColor(),
          postLinkColor = postLinkColor.toColor(),
          postSpoilerColor = postSpoilerColor.toColor(),
          postSpoilerRevealTextColor = postSpoilerRevealTextColor.toColor(),
          dividerColor = dividerColor.toColor(),
          scrollbarTrackColor = scrollbarTrackColor.toColor(),
          scrollbarThumbColorNormal = scrollbarThumbColorNormal.toColor(),
          scrollbarThumbColorDragged = scrollbarThumbColorDragged.toColor(),
          bookmarkCounterHasRepliesColor = bookmarkCounterHasRepliesColor.toColor(),
          bookmarkCounterNormalColor = bookmarkCounterNormalColor.toColor(),
        )
      }
    }

    companion object {
      fun fromChanTheme(chanTheme: ChanTheme): ChanThemeJson {
        return ChanThemeJson(
          name = chanTheme.name,
          isLightTheme = chanTheme.isLightTheme,
          lightStatusBar = chanTheme.lightStatusBar,
          lightNavBar = chanTheme.lightNavBar,
          accentColor = chanTheme.accentColor.toHexString(),
          gradientTopColor = chanTheme.gradientTopColor.toHexString(),
          gradientBottomColor = chanTheme.gradientBottomColor.toHexString(),
          behindGradientColor = chanTheme.behindGradientColor.toHexString(),
          backColor = chanTheme.backColor.toHexString(),
          backColorSecondary = chanTheme.backColorSecondary.toHexString(),
          selectedOnBackColor = chanTheme.selectedOnBackColor.toHexString(),
          highlightedOnBackColor = chanTheme.highlightedOnBackColor.toHexString(),
          errorColor = chanTheme.errorColor.toHexString(),
          textColorPrimary = chanTheme.textColorPrimary.toHexString(),
          textColorSecondary = chanTheme.textColorSecondary.toHexString(),
          textColorHint = chanTheme.textColorHint.toHexString(),
          postSavedReplyColor = chanTheme.postSavedReplyColor.toHexString(),
          postSubjectColor = chanTheme.postSubjectColor.toHexString(),
          postDetailsColor = chanTheme.postDetailsColor.toHexString(),
          postNameColor = chanTheme.postNameColor.toHexString(),
          postInlineQuoteColor = chanTheme.postInlineQuoteColor.toHexString(),
          postQuoteColor = chanTheme.postQuoteColor.toHexString(),
          postLinkColor = chanTheme.postLinkColor.toHexString(),
          postSpoilerColor = chanTheme.postSpoilerColor.toHexString(),
          postSpoilerRevealTextColor = chanTheme.postSpoilerRevealTextColor.toHexString(),
          dividerColor = chanTheme.dividerColor.toHexString(),
          scrollbarTrackColor = chanTheme.scrollbarTrackColor.toHexString(),
          scrollbarThumbColorNormal = chanTheme.scrollbarThumbColorNormal.toHexString(),
          scrollbarThumbColorDragged = chanTheme.scrollbarThumbColorDragged.toHexString(),
          bookmarkCounterHasRepliesColor = chanTheme.bookmarkCounterHasRepliesColor.toHexString(),
          bookmarkCounterNormalColor = chanTheme.bookmarkCounterNormalColor.toHexString(),
        )
      }

      private fun String?.toColorOrNull(): Color? {
        if (this == null) {
          return null
        }

        val str = if (this.startsWith("#")) {
          this
        } else {
          "#$this"
        }

        return try {
          Color(AndroidColor.parseColor(str))
        } catch (error: Throwable) {
          return null
        }
      }

      private fun String?.toColor(): Color {
        if (this == null) {
          throw ChanThemeException("Color is null")
        }

        val str = if (this.startsWith("#")) {
          this
        } else {
          "#$this"
        }

        return try {
          Color(AndroidColor.parseColor(str))
        } catch (error: Throwable) {
          throw ChanThemeException("Failed to parse \'$str\'", error)
        }
      }

      private fun Color.toHexString(): String {
        return buildString {
          append("#")
          append(String.format("%08X", toArgb()))
        }
      }
    }
  }

  class ChanThemeException : ClientException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

  companion object {
    private const val TAG = "ThemeParser"
  }
}