package com.github.k1rakishou.kurobaexlite.interactors

import android.content.Context
import android.os.Build
import com.github.k1rakishou.chan.core.mpv.MPVLib
import com.github.k1rakishou.chan.core.mpv.MpvSettings
import com.github.k1rakishou.kurobaexlite.helpers.Try
import com.github.k1rakishou.kurobaexlite.helpers.http_client.ProxiedOkHttpClient
import com.github.k1rakishou.kurobaexlite.helpers.isNotNullNorBlank
import com.github.k1rakishou.kurobaexlite.helpers.suspendCall
import com.github.k1rakishou.kurobaexlite.helpers.suspendConvertIntoJsonObjectWithAdapter
import com.github.k1rakishou.kurobaexlite.helpers.unwrap
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.EmptyBodyResponseException
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.Request
import okhttp3.internal.closeQuietly

class InstallMpvNativeLibrariesFromGithub(
  private val appContext: Context,
  private val moshi: Moshi,
  private val proxiedOkHttpClient: ProxiedOkHttpClient
) {
  private val githubReleaseResponsesListType = Types.newParameterizedType(List::class.java, GithubReleaseResponse::class.java)
  private val lookupTag = "v${MPVLib.SUPPORTED_MPV_PLAYER_VERSION}"

  suspend fun execute(mpvSettings: MpvSettings): Result<Unit> {
    return Result.Try { withContext(Dispatchers.IO) { executeInternal(mpvSettings) } }
  }

  private suspend fun executeInternal(mpvSettings: MpvSettings) {
    val abi = Build.SUPPORTED_ABIS.firstOrNull { abi -> abi.lowercase() in LIB_ABIS }
    logcat(TAG) { "Supported abis: \'${Build.SUPPORTED_ABIS.joinToString()}\', selected abi: \'${abi}\'" }

    if (abi == null) {
      throw MpvInstallLibsFromGithubException("No suitable ABI found: " +
        "expected one of: \'${LIB_ABIS.joinToString()}, " +
        "\'got: \'${Build.SUPPORTED_ABIS.joinToString()}\'")
    }

    val request = Request.Builder()
      .get()
      .url(KUROBAEX_MPV_LIBS_RELEASES_ENDPOINT)
      .build()

    val adapter = moshi.adapter<List<GithubReleaseResponse>>(githubReleaseResponsesListType)
    val githubReleases = proxiedOkHttpClient.okHttpClient().suspendConvertIntoJsonObjectWithAdapter(request, adapter)
      .unwrap()

    if (githubReleases == null) {
      throw MpvInstallLibsFromGithubException("Failed to convert json to GithubReleaseResponse")
    }

    val releaseForThisApp = githubReleases
      .firstOrNull { githubReleaseResponse -> githubReleaseResponse.tagName.equals(lookupTag, ignoreCase = true) }

    if (releaseForThisApp == null) {
      throw MpvInstallLibsFromGithubException("Failed to find libraries for \'${lookupTag}\' tag")
    }

    val githubAssetForThisApp = releaseForThisApp.assets.firstOrNull { githubAsset ->
      val archiveName = githubAsset.downloadUrl
        .removePrefix(KUROBAEX_MPV_LIBS_RELEASES_ENDPOINT)
        .removePrefix(lookupTag)

      if (archiveName.contains(abi, ignoreCase = true)) {
        return@firstOrNull true
      }

      return@firstOrNull false
    }

    if (githubAssetForThisApp == null) {
      val downloadUrls = releaseForThisApp.assets
        .joinToString(transform = { githubAsset -> githubAsset.downloadUrl })

      throw MpvInstallLibsFromGithubException("Failed to find download url for ABI ${abi}, downloadUrls: \'${downloadUrls}\'")
    }

    val downloadUrl = githubAssetForThisApp.downloadUrl
    logcat(TAG) { "Downloading \'${downloadUrl}\'" }

    val downloadRequest = Request.Builder()
      .get()
      .url(downloadUrl)
      .build()

    val response = proxiedOkHttpClient.okHttpClient().suspendCall(downloadRequest)

    if (!response.isSuccessful) {
      throw BadStatusResponseException(response.code)
    }

    val body = response.body
      ?: throw EmptyBodyResponseException()

    val mpvLibsZipArchiveFile = File(appContext.cacheDir, "mpv_libs.zip")
    logcat(TAG) { "mpvLibsZipArchiveFile: \'${mpvLibsZipArchiveFile.absolutePath}\'" }

    try {
      if (mpvLibsZipArchiveFile.exists()) {
        mpvLibsZipArchiveFile.delete()
      }

      if (!mpvLibsZipArchiveFile.createNewFile()) {
        throw MpvInstallLibsFromGithubException("Failed to create mpv libs output file on disk")
      }

      body.source().inputStream().use { inputStream ->
        mpvLibsZipArchiveFile.outputStream().use { outputStream ->
          inputStream.copyTo(outputStream)
        }
      }

      logcat(TAG) { "Done" }
      logcat(TAG) { "Deleting old lib files" }

      mpvSettings.mpvNativeLibsDir.listFiles()
        ?.forEach { libFile ->
          logcat(TAG) { "Deleting ${libFile.absolutePath}" }
          libFile.delete()
        }

      logcat(TAG) { "Done" }

      logcat(TAG) { "Extracting archived libs into \'${mpvSettings.mpvNativeLibsDir}\'" }
      extractArchiveAndMoveToLibsDirectory(mpvLibsZipArchiveFile, mpvSettings.mpvNativeLibsDir)
      logcat(TAG) { "Done" }
    } finally {
      mpvLibsZipArchiveFile.delete()
    }

    logcat(TAG) { "All done" }
  }

  private fun extractArchiveAndMoveToLibsDirectory(mpvLibsFile: File, mpvNativeLibsDir: File) {
    if (!mpvNativeLibsDir.exists()) {
      check(mpvNativeLibsDir.mkdirs()) { "Failed to create '${mpvNativeLibsDir.absolutePath}'" }
    }

    mpvLibsFile.inputStream().use { inputStream ->
      val zipInputStream = ZipInputStream(inputStream)
      var zipEntry: ZipEntry? = null
      var zipMalformed = true

      try {
        while (true) {
          zipEntry = zipInputStream.nextEntry
            ?: break

          val fileName = zipEntry.name
          logcat(TAG) { "Read zipEntry.name: \'${fileName}\'" }

          if (!zipEntry.isDirectory && fileName.endsWith(".so")) {
            val libName = fileName.split(delimiters = arrayOf("/", "\\")).lastOrNull()
            if (libName.isNotNullNorBlank()) {
              val outputMpvLibFile = File(mpvNativeLibsDir, libName)

              logcat(TAG) { "Moving \'${fileName}\' from archive to \'${outputMpvLibFile.absolutePath}\' file" }

              outputMpvLibFile.outputStream().use { outputStream ->
                zipInputStream.copyTo(outputStream)
              }
            } else {
              logcat(TAG) { "Invalid name: \'$libName\'" }
            }

            logcat(TAG) { "Done" }
          }

          zipInputStream.closeEntry()
          zipMalformed = false
        }
      } finally {
        zipInputStream.closeQuietly()
      }

      if (zipMalformed) {
        throw IOException("Failed to open mpv libs zip archive: '${mpvLibsFile.absolutePath}'")
      }
    }
  }

  class MpvInstallLibsFromGithubException(message: String) : Exception(message)

  @JsonClass(generateAdapter = true)
  data class GithubReleaseResponse(
    @Json(name = "tag_name")
    val tagName: String,
    @Json(name = "assets")
    val assets: List<GithubAsset>
  )

  @JsonClass(generateAdapter = true)
  data class GithubAsset(
    @Json(name = "name")
    val name: String,
    @Json(name = "browser_download_url")
    val downloadUrl: String
  )

  companion object {
    private const val TAG = "InstallMpvNativeLibrariesFromGithubUseCase"
    private const val KUROBAEX_MPV_LIBS_RELEASES_ENDPOINT = "https://api.github.com/repos/K1rakishou/KurobaEx-mpv-libs/releases"

    private val LIB_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
      .map { it.lowercase() }
  }

}