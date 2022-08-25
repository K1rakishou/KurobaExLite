package com.github.k1rakishou.kurobaexlite.interactors.image_search

import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.appendCookieHeader
import com.github.k1rakishou.kurobaexlite.helpers.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.helpers.extractFileNameExtension
import com.github.k1rakishou.kurobaexlite.helpers.fixUrlOrNull
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorEmpty
import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.github.k1rakishou.kurobaexlite.model.FirewallDetectedException
import com.github.k1rakishou.kurobaexlite.model.FirewallType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup

class YandexImageSearch(
  private val proxiedOkHttpClient: ProxiedOkHttpClient,
  private val moshi: Moshi
) {

  suspend fun await(
    searchUrl: HttpUrl,
    cookies: String?
  ): Result<List<ImageSearchResult>> {
    return Result.Try {
      withContext(Dispatchers.IO) {
        searchInternal(searchUrl, cookies)
      }
    }
  }

  private suspend fun searchInternal(searchUrl: HttpUrl, cookies: String?): List<ImageSearchResult> {
    val request = with(Request.Builder()) {
      url(searchUrl)
      get()

      if (cookies.isNotNullNorEmpty()) {
        appendCookieHeader(cookies)
      }

      build()
    }

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(request)
      .unwrap()

    if (!response.isSuccessful) {
      throw BadStatusResponseException(response.code)
    }

    val body = response.body
    if (body == null) {
      throw EmptyBodyResponseException()
    }

    return body.byteStream().use { bodyStream ->
      val yandexImageSearchDocument = Jsoup.parse(
        bodyStream,
        StandardCharsets.UTF_8.name(),
        searchUrl.toString()
      )

      val captchaRequired = yandexImageSearchDocument
        .body()
        .select("div[id=smartcaptcha-status]")
        .firstOrNull()

      if (captchaRequired != null) {
        logcat(TAG) { "smartcaptcha-status element detected, need to solve captcha" }

        throw FirewallDetectedException(
          firewallType = FirewallType.YandexSmartCaptcha,
          requestUrl = searchUrl
        )
      }

      val serpControllerContent = yandexImageSearchDocument
        .body()
        .select("div[class=serp-controller__content]")
        .firstOrNull()

      if (serpControllerContent == null) {
        logcat(TAG) { "serp-controller__content element not found" }
        return@use emptyList()
      }

      val serpItems = serpControllerContent
        .childNodes()
        .firstOrNull { node ->
          node.attributes().any { attribute ->
            attribute.value.contains("serp-list")
          }
        }
        ?.childNodes()

      if (serpItems.isNullOrEmpty()) {
        logcat(TAG) { "No serp-item elements found" }
        return@use emptyList()
      }

      val dataBems = serpItems
        .map { serpItemNode -> serpItemNode.attr("data-bem") }
        .filter { dataBemJsonRaw -> dataBemJsonRaw.isNotBlank() }
        .mapNotNull { dataBemsJson ->
          try {
            moshi.adapter(DataBem::class.java).fromJson(dataBemsJson)
          } catch (error: Throwable) {
            logcatError(TAG) {
              "Failed to convert dataBemsJson into DataBem object, " +
                "error=${error.errorMessageOrClassName()}"
            }

            null
          }
        }

      if (dataBems.isEmpty()) {
        logcat(TAG) { "No data-bem elements found" }
        return@use emptyList()
      }

      return@use dataBems.mapNotNull { dataBem ->
        val thumbUrl = fixUrlOrNull(dataBem.serpItem?.thumb?.url)?.toHttpUrlOrNull()
          ?: return@mapNotNull null

        val combinedPreviews = dataBem.serpItem?.combinedPreviews()

        val preview = combinedPreviews?.firstOrNull { preview -> preview.isValid() }
          ?: combinedPreviews?.firstOrNull()
          ?: return@mapNotNull null

        val fullUrls = combinedPreviews
          ?.mapNotNull { preview -> fixUrlOrNull(preview.url)?.toHttpUrlOrNull() }
          ?.toSet()
          ?.toList()
          ?: return@mapNotNull null

        val extension = fullUrls
          .mapNotNull { fullUrl -> fullUrl.toString().extractFileNameExtension() }
          .firstOrNull()

        return@mapNotNull ImageSearchResult(
          thumbnailUrl = thumbUrl,
          fullImageUrls = fullUrls,
          sizeInByte = preview.size,
          width = preview.width,
          height = preview.height,
          extension = extension
        )
      }
    }
  }

  @JsonClass(generateAdapter = true)
  data class DataBem(
    @Json(name = "serp-item") val serpItem: SerpItem? = null
  )

  @JsonClass(generateAdapter = true)
  data class SerpItem(
    @Json(name = "thumb") val thumb: Thumb?,
    @Json(name = "preview") val preview: List<Preview>?,
    @Json(name = "dups") val dups: List<Preview>?
  ) {

    fun combinedPreviews(): List<Preview> {
      return (preview ?: emptyList()) + (dups ?: emptyList())
    }

  }

  @JsonClass(generateAdapter = true)
  data class Thumb(
    @Json(name = "url") val url: String?
  )

  @JsonClass(generateAdapter = true)
  data class Preview(
    @Json(name = "fileSizeInBytes") val size: Long?,
    @Json(name = "w") val width: Int?,
    @Json(name = "h") val height: Int?,
    @Json(name = "url") val url: String?,
  ) {
    fun isValid(): Boolean {
      return size != null && url != null && width != null && height != null
    }
  }

  data class ImageSearchResult(
    val thumbnailUrl: HttpUrl,
    val fullImageUrls: List<HttpUrl>,
    val sizeInByte: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val extension: String? = null
  ) {

    fun hasImageInfo(): Boolean {
      return sizeInByte != null || width != null || height != null || extension != null
    }

  }

  companion object {
    private const val TAG = "YandexImageSearch"
  }

}