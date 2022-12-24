package com.github.k1rakishou.kurobaexlite.helpers.post_bind.processors

import android.util.LruCache
import com.github.k1rakishou.kurobaexlite.helpers.html.HtmlUnescape
import com.github.k1rakishou.kurobaexlite.helpers.network.http_client.IKurobaOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.parser.PostCommentApplier
import com.github.k1rakishou.kurobaexlite.helpers.parser.TextPart
import com.github.k1rakishou.kurobaexlite.helpers.settings.AppSettings
import com.github.k1rakishou.kurobaexlite.helpers.util.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kurobaexlite.helpers.util.groupOrNull
import com.github.k1rakishou.kurobaexlite.helpers.util.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.helpers.util.parallelForEach
import com.github.k1rakishou.kurobaexlite.helpers.util.substringSafe
import com.github.k1rakishou.kurobaexlite.helpers.util.suspendCall
import com.github.k1rakishou.kurobaexlite.model.cache.IChanPostCache
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ChanDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import logcat.logcat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ThreadLocalRandom
import java.util.regex.Pattern


class Chan4MathTagProcessor(
  private val chanPostCache: IChanPostCache,
  private val proxiedOkHttpClient: IKurobaOkHttpClient,
  private val appSettings: AppSettings
) : IPostProcessor {
  private val mutex = Mutex()
  private val _cache = LruCache<PostDescriptor, PostCachedFormulas>(1024)

  override suspend fun isCached(postDescriptor: PostDescriptor): Boolean {
    return mutex.withLock { _cache.get(postDescriptor) != null }
  }

  override suspend fun removeCached(chanDescriptor: ChanDescriptor) {
    mutex.withLock {
      val toRemove = mutableListWithCap<PostDescriptor>(32)

      _cache.snapshot().keys.forEach { postDescriptor ->
        val shouldBeRemoved = when (chanDescriptor) {
          is CatalogDescriptor -> postDescriptor.catalogDescriptor == chanDescriptor
          is ThreadDescriptor -> postDescriptor.threadDescriptor == chanDescriptor
        }

        if (shouldBeRemoved) {
          toRemove += postDescriptor
        }
      }

      if (toRemove.isEmpty()) {
        return@withLock
      }

      toRemove.forEach { postDescriptor -> _cache.remove(postDescriptor) }
    }
  }

  suspend fun getCachedFormulaBySanitizedRawFormula(
    postDescriptor: PostDescriptor,
    formulaRaw: String
  ): CachedFormula? {
    val formulaSanitized = sanitizeFormula(formulaRaw)

    return getCacheEntryOrNull(postDescriptor)?.getCachedFormulaBySanitizedFormula(formulaSanitized)
  }

  override suspend fun applyData(
    textPart: TextPart,
    postDescriptor: PostDescriptor
  ): IPostProcessor.AppliedDataResult? {
    val postCachedFormulas = getCacheEntryOrNull(postDescriptor)
      ?: return null

    val formulaInfoList = findFormulas(textPart.text)
    if (formulaInfoList.isEmpty()) {
      return null
    }

    val annotations = mutableListOf<IPostProcessor.InlinedContent>()

     formulaInfoList.forEach { formulaInfo ->
       val formulaImageUrl = postCachedFormulas.getCachedFormulaBySanitizedFormula(formulaInfo.formulaSanitized)
         ?.formulaImageUrl
         ?.toString()
         ?: return@forEach

       logcat(LogPriority.VERBOSE, TAG) {
         "applyData(${postDescriptor}) Applying ${formulaImageUrl} to " +
           "${textPart.text.substringSafe(formulaInfo.startIndex, formulaInfo.endIndex)}"
       }

       annotations += IPostProcessor.InlinedContent(
         annotation = PostCommentApplier.ANNOTATION_INLINED_IMAGE,
         alternateText = formulaImageUrl,
         startIndex = formulaInfo.startIndex,
         endIndex = formulaInfo.endIndex
       )
    }

    return IPostProcessor.AppliedDataResult(textPart, annotations)
  }

  override suspend fun process(
    isCatalogMode: Boolean,
    postsParsedOnce: Boolean,
    postDescriptor: PostDescriptor
  ) : Boolean {
    val postData = if (isCatalogMode) {
      chanPostCache.getCatalogPost(postDescriptor)
    } else {
      chanPostCache.getThreadPost(postDescriptor)
    }

    if (postData == null) {
      return false
    }

    val formulaInfoList = findFormulas(postData.postCommentUnparsed)
    if (formulaInfoList.isEmpty()) {
      return false
    }

    val foundLinks = processFormulas(postDescriptor, formulaInfoList)
    if (foundLinks.isNotEmpty()) {
      logcat(LogPriority.VERBOSE, TAG) { "process(${postDescriptor}) foundLinks=${foundLinks}" }
    }

    return foundLinks.isNotEmpty()
  }

  private fun findFormulas(postCommentUnparsed: String): MutableList<FormulaInfo> {
    val formulaInfoList = mutableListOf<FormulaInfo>()
    val matcher = MATH_TAG_PATTERN.matcher(postCommentUnparsed)

    while (matcher.find()) {
      val formulaRaw = matcher.groupOrNull(0) ?: continue

      val startIndex = matcher.start(0)
      val endIndex = matcher.end(0)

      formulaInfoList += FormulaInfo(
        startIndex = startIndex,
        endIndex = endIndex,
        formulaRaw = formulaRaw,
        formulaSanitized = sanitizeFormula(formulaRaw)
      )
    }

    return formulaInfoList
  }

  private suspend fun processFormulas(
    postDescriptor: PostDescriptor,
    formulaInfoList: MutableList<FormulaInfo>
  ): List<HttpUrl> {
    return parallelForEach(
      dataList = formulaInfoList,
      parallelization = 4,
      dispatcher = Dispatchers.IO
    ) { formulaInfo ->
      val cachedFormulaImageUrl = getOrCreateCacheEntry(postDescriptor)
        .getCachedFormulaByRawFormula(formulaInfo.formulaRaw)
        ?.formulaImageUrl

      if (cachedFormulaImageUrl != null) {
        return@parallelForEach null
      }

      val postBody = formatFetchFormulaImageUrlBody(formulaInfo)

      val request = Request.Builder()
        .url("https://www.quicklatex.com/latex3.f")
        .post(postBody.toRequestBody())
        .build()

      val requestResponse = proxiedOkHttpClient.okHttpClient().suspendCall(request)
        .onFailure { error ->
          logcatError(TAG) { "Failed to fetch quicklatex formula url, error: ${error.asLogIfImportantOrErrorMessage()}" }
        }
        .getOrNull()

      if (requestResponse == null) {
        return@parallelForEach null
      }

      return@parallelForEach requestResponse.use { response ->
        if (!response.isSuccessful) {
          logcatError(TAG) { "Failed to fetch quicklatex formula url, bad status code: ${response.code}" }
          return@use null
        }

        val responseBody = response.body
        if (responseBody == null) {
          logcatError(TAG) { "Failed to fetch quicklatex formula url, response body is null" }
          return@use null
        }

        val responseBodyString = responseBody.string()

        val errorMatcher = ERROR_PATTERN.matcher(responseBodyString)
        if (errorMatcher.find()) {
          val errorMessage = errorMatcher.groupOrNull(1)
          val errorCode = errorMatcher.groupOrNull(2)

          val errorMessageFormatted = buildString {
            append("Error message: ")

            if (errorMessage.isNotNullNorBlank()) {
              append(errorMessage)
            } else {
              append("No error message")
            }

            append(", ")
            append("Error code: ")

            if (errorCode != null) {
              append(errorCode)
            } else {
              append("No error code")
            }
          }

          logcatError(TAG) { "Failed to fetch quicklatex formula url, server returned error \'${errorMessageFormatted}\'" }
          return@use null
        }

        val resultMatcher = RESULT_PATTERN.matcher(responseBodyString)
        if (!resultMatcher.find()) {
          logcatError(TAG) { "Failed to fetch quicklatex formula url, no link found in the response" }
          return@use null
        }

        val formulaImageLink = resultMatcher.groupOrNull(1)?.toHttpUrlOrNull()
        if (formulaImageLink == null) {
          logcatError(TAG) { "Failed to fetch quicklatex formula url, formulaImageLink == null" }
          return@use null
        }

        val imageWidth = resultMatcher.groupOrNull(3)?.toIntOrNull()
        if (imageWidth == null || imageWidth <= 0) {
          logcatError(TAG) { "Failed to fetch quicklatex formula url, imageWidth == null or < 0" }
          return@use null
        }

        val imageHeight = resultMatcher.groupOrNull(4)?.toIntOrNull()
        if (imageHeight == null || imageHeight <= 0) {
          logcatError(TAG) { "Failed to fetch quicklatex formula url, imageHeight == null or < 0" }
          return@use null
        }

        getOrCreateCacheEntry(postDescriptor).addFormula(
          formulaRaw = formulaInfo.formulaRaw,
          formulaSanitized = formulaInfo.formulaSanitized,
          formulaImageUrl = formulaImageLink,
          imageWidth = imageWidth,
          imageHeight = imageHeight
        )

        return@use formulaImageLink
      }
    }
  }

  private suspend fun formatFetchFormulaImageUrlBody(
    formulaInfo: FormulaInfo
  ): String {
    val random = ThreadLocalRandom.current().nextInt(100).toDouble()
    val fontSizePx = appSettings.calculateFontSizeInPixels(16)
    val foregroundColor = String.format("%06X", 0x0)
    val backgroundColor = String.format("%06X", 0xFFFFFF)

    return "formula=${formulaInfo.formulaSanitized}&fsize=${fontSizePx}px&" +
      "fcolor=${foregroundColor}&bcolor=${backgroundColor}&mode=0&out=1" +
      "&preamble=\\\\usepackage{amsmath}\\r\\n\\\\usepackage{amsfonts}\\r\\n\\\\usepackage{amssymb}" +
      "&rnd=${random}&remhost=quicklatex.com"
  }

  private suspend fun getCacheEntryOrNull(postDescriptor: PostDescriptor): PostCachedFormulas? {
    return mutex.withLock { _cache.get(postDescriptor) }
  }

  private suspend fun getOrCreateCacheEntry(postDescriptor: PostDescriptor): PostCachedFormulas {
    return mutex.withLock {
      val cached = _cache.get(postDescriptor)
      if (cached != null) {
        return@withLock cached
      }

      val postCachedFormulas = PostCachedFormulas()
      _cache.put(postDescriptor, postCachedFormulas)

      return@withLock postCachedFormulas
    }
  }

  private class PostCachedFormulas {
    private val mutex = Mutex()
    private val formulas = mutableListOf<CachedFormula>()

    suspend fun addFormula(
      formulaRaw: String,
      formulaSanitized: String,
      formulaImageUrl: HttpUrl,
      imageWidth: Int,
      imageHeight: Int
    ) {
      mutex.withLock {
        val previousFormulaIndex = formulas.indexOfFirst { cachedFormula -> cachedFormula.formulaRaw == formulaRaw }
        if (previousFormulaIndex < 0) {
          formulas += CachedFormula(
            formulaRaw = formulaRaw,
            formulaSanitized = formulaSanitized,
            formulaImageUrl = formulaImageUrl,
            imageWidth = imageWidth,
            imageHeight = imageHeight
          )
        } else {
          formulas[previousFormulaIndex] = CachedFormula(
            formulaRaw = formulaRaw,
            formulaSanitized = formulaSanitized,
            formulaImageUrl = formulaImageUrl,
            imageWidth = imageWidth,
            imageHeight = imageHeight
          )
        }
      }
    }

    suspend fun getCachedFormulaByRawFormula(formulaRaw: String): CachedFormula? {
      return mutex.withLock {
        formulas.firstOrNull { cachedFormula -> cachedFormula.formulaRaw == formulaRaw }
      }
    }

    suspend fun getCachedFormulaBySanitizedFormula(formulaSanitized: String): CachedFormula? {
      return mutex.withLock {
        formulas.firstOrNull { cachedFormula -> cachedFormula.formulaSanitized == formulaSanitized }
      }
    }

  }

  data class CachedFormula(
    val formulaRaw: String,
    val formulaSanitized: String,
    val formulaImageUrl: HttpUrl,
    val imageWidth: Int,
    val imageHeight: Int
  )

  private data class FormulaInfo(
    val startIndex: Int,
    val endIndex: Int,
    val formulaRaw: String,
    val formulaSanitized: String
  )

  companion object {
    private const val TAG = "Chan4MathTagProcessor"

    private val MATH_TAG_PATTERN = Pattern.compile("\\[(math|eqn)](.*?)\\[/\\1]", Pattern.DOTALL)
    private val ERROR_PATTERN = Pattern.compile("Error:\\s+(.+)\\s+\\=\\s+(-?\\d+)")
    private val RESULT_PATTERN = Pattern.compile("(https:\\/\\/.*?)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)")

    private fun sanitizeFormula(formulaRaw: String): String {
      val sanitized = formulaRaw
        .replace("[math]", "")
        .replace("[eqn]", "")
        .replace("[/math]", "")
        .replace("[/eqn]", "")
        .replace("<wbr>", "")
        .replace("%", "%25")

      return HtmlUnescape.unescape(sanitized)
    }
  }
}