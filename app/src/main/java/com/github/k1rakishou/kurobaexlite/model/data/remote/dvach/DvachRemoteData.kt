package com.github.k1rakishou.kurobaexlite.model.data.remote.dvach

import com.github.k1rakishou.kurobaexlite.helpers.util.extractFileNameExtension
import com.github.k1rakishou.kurobaexlite.helpers.util.removeExtensionFromFileName
import com.github.k1rakishou.kurobaexlite.model.data.local.PostImageData
import com.github.k1rakishou.kurobaexlite.model.descriptors.CatalogDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import com.github.k1rakishou.kurobaexlite.sites.Site
import com.github.k1rakishou.kurobaexlite.sites.dvach.Dvach
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.jsoup.parser.Parser

@JsonClass(generateAdapter = true)
data class DvachBoardDataJson(
  @Json(name = "id") val boardCode: String?,
  @Json(name = "name") val boardTitle: String?,
  @Json(name = "info_outer") val boardDescription: String?,
  @Json(name = "category") val category: String?,
  @Json(name = "bump_limit") val bumpLimit: Int?,
) {

  val workSafe: Boolean
    get() = category?.equals("Взрослым", ignoreCase = true) == true

}

@JsonClass(generateAdapter = true)
data class DvachCatalog(
  @Json(name = "board")
  val board: DvachBoardInfo?,
  @Json(name = "threads")
  val threads: List<DvachPost>?,
  @Json(name = "error")
  val error: DvachError?
)

@JsonClass(generateAdapter = true)
data class DvachSearchResult(
  @Json(name = "board")
  val board: DvachBoardInfo?,
  @Json(name = "posts")
  val posts: List<DvachPost>?,
  @Json(name = "error")
  val error: DvachError?
)

interface DvachThread {
  val error: DvachError?
  val threadPosts: List<DvachPost>?
}

@JsonClass(generateAdapter = true)
data class DvachThreadFull(
  @Json(name = "board")
  val board: DvachBoardInfo?,
  @Json(name = "threads")
  val threads: List<DvachThreadInternal>?,
  @Json(name = "posts_count")
  val postsCount: Int?,
  @Json(name = "files_count")
  val filesCount: Int?,
  @Json(name = "unique_posters")
  val postersCount: Int?,
  @Json(name = "error")
  override val error: DvachError?
) : DvachThread {
  override val threadPosts: List<DvachPost>?
    get() = threads?.firstOrNull()?.posts
}

@JsonClass(generateAdapter = true)
data class DvachThreadPartial(
  @Json(name = "posts")
  val posts: List<DvachPost>?,
  @Json(name = "error")
  override val error: DvachError?
) : DvachThread {
  override val threadPosts: List<DvachPost>? = posts
}

@JsonClass(generateAdapter = true)
data class DvachThreadInternal(
  @Json(name = "posts")
  val posts: List<DvachPost>?,
)

@JsonClass(generateAdapter = true)
data class DvachBoardInfo(
  @Json(name = "bump_limit")
  val bumpLimit: Int?,
  @Json(name = "default_name")
  val defaultName: String?
)

@JsonClass(generateAdapter = true)
data class DvachPost(
  val num: Long,
  val op: Long,
  val parent: Long,
  val banned: Long,
  val comment: String,
  val subject: String,
  val date: String,
  val email: String,
  val name: String,
  val closed: Int,
  val sticky: Int,
  val endless: Int,
  val timestamp: Long,
  val trip: String,
  val icon: String?,
  val lasthit: Long,
  @Json(name = "posts_count")
  val postsCount: Int?,
  @Json(name = "files_count")
  val filesCount: Int?,
  @Json(name = "unique_posters")
  val postersCount: Int?,
  val files: List<DvachFile>?
) {
  val isOp: Boolean
    get() = parent == 0L
  val opMark: Boolean
    get() = op == 1L
  val sage: Boolean
    get() = name.contains("ID:&nbsp;Heaven") || email.contains("mailto:sage")
  val isSticky: Boolean
    get() = sticky > 0L
  val isEndless: Boolean
    get() = endless == 1
  val isBanned: Boolean
    get() = banned == 1L

  val originalPostNo: Long
    get() {
      if (parent != 0L) {
        return parent
      } else {
        return num
      }
    }

  fun threadDescriptor(catalogDescriptor: CatalogDescriptor): ThreadDescriptor {
    return ThreadDescriptor.create(
      Dvach.SITE_KEY,
      catalogDescriptor.boardCode,
      originalPostNo
    )
  }

}

@JsonClass(generateAdapter = true)
data class DvachBookmarkCatalogInfo(
  @Json(name = "board")
  val board: DvachThreadBoardInfo?,
  @Json(name = "threads")
  val threads: List<DvachThreadPostInfo>,
  @Json(name = "is_closed")
  val closed: Int
) {
  val isClosed: Boolean
    get() = closed == 1
}

@JsonClass(generateAdapter = true)
data class DvachThreadBoardInfo(
  @Json(name = "bump_limit")
  val bumpLimit: Int,
)

@JsonClass(generateAdapter = true)
data class DvachThreadPostInfo(
  @Json(name = "posts")
  val posts: List<DvachBookmarkPostInfo>
)

@JsonClass(generateAdapter = true)
data class DvachBookmarkPostInfo(
  val num: Long,
  val parent: Long,
  val comment: String?,
  val subject: String?,
  val sticky: Long,
  val endless: Long,
  val files: List<DvachBookmarkFileInfo>?
) {
  val isOp: Boolean
    get() = parent == 0L
  val isSticky: Boolean
    get() = sticky == 1L
  val isEndless: Boolean
    get() = endless == 1L
  val firstFile: DvachBookmarkFileInfo?
    get() = files?.firstOrNull()
}

@JsonClass(generateAdapter = true)
data class DvachBookmarkFileInfo(
  val thumbnail: String
)

@JsonClass(generateAdapter = true)
data class DvachFile(
  val fullname: String?,
  val md5: String?,
  val name: String?,
  val path: String?,
  val size: Long,
  val thumbnail: String,
  @Json(name = "tn_height")
  val tnHeight: Long,
  @Json(name = "tn_width")
  val tnWidth: Long,
  val type: Long,
  val width: Int,
  val height: Int
) {

  fun toPostImageData(
    postImageInfo: Site.PostImageInfo,
    postDescriptor: PostDescriptor,
  ): PostImageData? {
    if (path?.contains("/stickers/", ignoreCase = true) == true) {
      return null
    }

    if (postImageInfo !is Dvach.PostImageInfo) {
      return null
    }

    var fileExt: String? = null
    var serverFileName: String? = null

    if (name != null) {
      fileExt = name.extractFileNameExtension()
      serverFileName = name.removeExtensionFromFileName()
    }

    val originalFileName = if (fullname.isNullOrEmpty()) {
      serverFileName
    } else {
      fullname.removeExtensionFromFileName()
    }

    if (path == null || serverFileName == null) {
      return null
    }

    val thumbnailUrl = postImageInfo.thumbnailUrl(postImageInfo.wrapThumbnailParameters(thumbnail))
    if (thumbnailUrl == null) {
      return null
    }

    val fullImageUrl = postImageInfo.fullUrl(postImageInfo.wrapFullImageParameters(path))
    if (fullImageUrl == null) {
      return null
    }

    return PostImageData(
      thumbnailUrl = thumbnailUrl,
      fullImageUrl = fullImageUrl,
      originalFileNameEscaped = Parser.unescapeEntities(originalFileName, false),
      serverFileName = serverFileName,
      ext = fileExt ?: "jpg",
      width = width,
      height = height,
      fileSize = size * 1024,
      thumbnailSpoiler = null,
      ownerPostDescriptor = postDescriptor
    )
  }

}

@JsonClass(generateAdapter = true)
data class DvachCatalogPageJson(
  val board: DvachCatalogPageBoardInfoJson?,
  val threads: List<DvachCatalogPageThreadJson>
) {

  fun isValid(): Boolean {
    return board != null && board.isValid()
  }

}

@JsonClass(generateAdapter = true)
data class DvachCatalogPageBoardInfoJson(
  @Json(name = "threads_per_page") val threadsPerPage: Int?,
) {

  fun isValid(): Boolean {
    return threadsPerPage != null && threadsPerPage > 0
  }

}

@JsonClass(generateAdapter = true)
data class DvachCatalogPageThreadJson(
  @Json(name = "num") val postNo: Long,
)

@JsonClass(generateAdapter = true)
data class DvachPasscodeResult(
  val result: Int,
  val passcode: DvachPasscodeInfo?,
  val error: DvachError?,
)

@JsonClass(generateAdapter = true)
data class DvachPasscodeInfo(
  val type: String,
  val expires: Int,
)

@JsonClass(generateAdapter = true)
data class DvachError(
  @Json(name = "code")
  val errorCode: Int,
  val message: String
) {

  fun message(): String {
    return when (errorCode) {
      NO_ERROR -> "No error"
      BOARD_DOES_NOT_EXIST -> "Board does not exist"
      THREAD_DOES_NOT_EXIST -> "Thread does not exist"
      NO_ACCESS -> "No access"
      THREAD_IS_CLOSED -> "Thread is closed"
      BOARD_IS_CLOSED -> "Board is closed"
      BOARD_IS_VIP_ONLY -> "Board is VIP (passcode) only"
      else -> "Unsupported error code: $errorCode"
    }
  }

  fun isActuallyError(): Boolean = errorCode != 0

  fun isThreadDeleted(): Boolean {
    when (errorCode) {
      BOARD_IS_CLOSED,
      BOARD_DOES_NOT_EXIST,
      BOARD_IS_VIP_ONLY,
      THREAD_DOES_NOT_EXIST -> return true
      else -> return false
    }
  }

  fun isThreadClosed(): Boolean {
    when (errorCode) {
      THREAD_IS_CLOSED -> return true
      else -> return false
    }
  }

  fun cantAccessCatalog(): Boolean {
    when (errorCode) {
      BOARD_DOES_NOT_EXIST,
      BOARD_IS_VIP_ONLY,
      BOARD_IS_CLOSED,
      NO_ACCESS -> return true
      else -> return false
    }
  }

  companion object {
    private const val NO_ERROR = 0
    private const val BOARD_DOES_NOT_EXIST = -2
    private const val THREAD_DOES_NOT_EXIST = -3
    private const val NO_ACCESS = -4
    private const val THREAD_IS_CLOSED = -7
    private const val BOARD_IS_CLOSED = -41
    private const val BOARD_IS_VIP_ONLY = -42

    fun isNotFoundError(errorCode: Int): Boolean {
      return errorCode == BOARD_DOES_NOT_EXIST || errorCode == THREAD_DOES_NOT_EXIST
    }
  }

}