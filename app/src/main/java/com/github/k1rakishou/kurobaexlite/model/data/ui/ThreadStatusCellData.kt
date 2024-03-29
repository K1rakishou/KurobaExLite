package com.github.k1rakishou.kurobaexlite.model.data.ui

import android.content.Context
import com.github.k1rakishou.kurobaexlite.R
import com.github.k1rakishou.kurobaexlite.helpers.util.errorMessageOrClassName
import com.github.k1rakishou.kurobaexlite.model.BadStatusResponseException
import com.github.k1rakishou.kurobaexlite.model.ClientException
import com.github.k1rakishou.kurobaexlite.model.data.ui.post.PostCellData
import com.github.k1rakishou.kurobaexlite.model.repository.CatalogPagesRepository
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import org.apache.http.conn.ConnectTimeoutException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

data class ThreadStatusCellData(
  val totalReplies: Int = 0,
  val totalImages: Int = 0,
  val totalPosters: Int = 0,
  val threadPage: CatalogPagesRepository.ThreadPage?,
  val archived: Boolean? = null,
  val closed: Boolean? = null,
  val sticky: PostCellData.Sticky? = null,
  val bumpLimit: Boolean? = null,
  val imageLimit: Boolean? = null
) {

  fun isThreadDeleted(lastLoadError: Throwable?): Boolean {
    if (lastLoadError is BadStatusResponseException && lastLoadError.isNotFoundError()) {
      return true
    }

    return false
  }

  fun errorMessage(context: Context, lastLoadError: Throwable?): String {
    val error = requireNotNull(lastLoadError) { "lastLoadError is null" }

    return when (error) {
      is CancellationException -> context.resources.getString(R.string.thread_load_failed_canceled)
      is ConnectTimeoutException,
      is SocketTimeoutException -> context.resources.getString(R.string.thread_load_failed_timeout)
      is SocketException,
      is UnknownHostException -> context.resources.getString(R.string.thread_load_failed_network)
      is BadStatusResponseException -> {
        when {
          error.isAuthError() -> context.resources.getString(R.string.thread_load_failed_auth_error)
          error.isForbiddenError() -> context.resources.getString(R.string.thread_load_failed_forbidden_error)
          error.isNotFoundError() -> context.resources.getString(R.string.thread_load_failed_not_found)
          else -> context.resources.getString(R.string.thread_load_failed_server, error.status)
        }
      }
      is SSLException -> {
        if (error.message != null) {
          val message = error.message!!
          context.resources.getString(R.string.thread_load_failed_ssl_with_reason, message)
        } else {
          context.resources.getString(R.string.thread_load_failed_ssl)
        }
      }
      is JsonDataException,
      is JsonEncodingException,
      is ClientException -> error.errorMessageOrClassName(userReadable = true)
      else -> {
        if (error.message != null) {
          return error.message!!
        }

        return context.resources.getString(R.string.thread_load_failed_unknown, error.javaClass.simpleName)
      }
    }
  }

  fun canRefresh(): Boolean {
    if (archived == true) {
      return false
    }

    if (closed == true) {
      return false
    }

    return true
  }

}