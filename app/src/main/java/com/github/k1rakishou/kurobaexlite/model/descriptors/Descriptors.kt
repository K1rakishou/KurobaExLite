package com.github.k1rakishou.kurobaexlite.model.descriptors

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.helpers.util.readUtfString
import com.github.k1rakishou.kurobaexlite.helpers.util.writeUtfString
import kotlinx.parcelize.Parcelize
import okio.Buffer

@JvmInline
// TODO(KurobaEx):
//  @Immutable doesn't work together with "value class"!!!
//  It makes the whole class unstable for Compose.
@Immutable
@Parcelize
value class SiteKey(val key: String) : Parcelable {
  override fun toString(): String {
    return key
  }
}

@Immutable
sealed class ChanDescriptor : Parcelable {
  abstract val siteKey: SiteKey
  abstract val boardCode: String

  abstract fun catalogDescriptor(): CatalogDescriptor
  abstract fun asKey(): String
  abstract fun asReadableString(): String
}

@Immutable
@Parcelize
data class CatalogDescriptor(
  override val siteKey: SiteKey,
  override val boardCode: String
) : ChanDescriptor() {
  val siteKeyActual: String
    get() = siteKey.key

  fun serialize(buff: Buffer?): Buffer {
    val buffer = buff ?: Buffer()

    buffer.writeUtfString(siteKeyActual)
    buffer.writeUtfString(boardCode)

    return buffer
  }

  override fun catalogDescriptor(): CatalogDescriptor {
    return this
  }

  override fun asKey(): String {
    return buildString(capacity = 32) {
      append("cd")
      append("_")
      append(siteKeyActual)
      append("_")
      append(boardCode)
    }
  }

  override fun asReadableString(): String {
    return buildString(capacity = 32) {
      append(siteKeyActual)
      append("/")
      append(boardCode)
    }
  }


  override fun toString(): String {
    return buildString(capacity = 32) {
      append("CatalogDescriptor(")
      append(siteKeyActual)
      append("/")
      append(boardCode)
      append(")")
    }
  }

  companion object {
    fun deserialize(buffer: Buffer): CatalogDescriptor {
      val siteKey = SiteKey(buffer.readUtfString())
      val boardCode = buffer.readUtfString()

      return CatalogDescriptor(siteKey, boardCode)
    }
  }
}

@Immutable
@Parcelize
data class ThreadDescriptor(
  val catalogDescriptor: CatalogDescriptor,
  val threadNo: Long
) : ChanDescriptor() {

  init {
    check(threadNo > 0L) { "Bad threadNo: ${threadNo}" }
  }

  override val siteKey: SiteKey
    get() = catalogDescriptor.siteKey
  val siteKeyActual: String
    get() = siteKey.key
  override val boardCode: String
    get() = catalogDescriptor.boardCode

  fun toOriginalPostDescriptor(): PostDescriptor {
    return PostDescriptor(threadDescriptor = this, postNo = threadNo)
  }

  fun serialize(buff: Buffer?): Buffer {
    val buffer = buff ?: Buffer()

    catalogDescriptor.serialize(buffer)
    buffer.writeLong(threadNo)

    return buffer
  }

  override fun catalogDescriptor(): CatalogDescriptor {
    return catalogDescriptor
  }

  override fun asKey(): String {
    return buildString(capacity = 32) {
      append("td")
      append("_")
      append(siteKeyActual)
      append("_")
      append(boardCode)
      append("_")
      append(threadNo)
    }
  }

  override fun asReadableString(): String {
    return buildString(capacity = 32) {
      append(siteKeyActual)
      append("/")
      append(boardCode)
      append("/")
      append(threadNo)
    }
  }

  override fun toString(): String {
    return buildString(capacity = 32) {
      append("ThreadDescriptor(")
      append(siteKeyActual)
      append("/")
      append(boardCode)
      append("/")
      append(threadNo)
      append(")")
    }
  }

  companion object {
    fun create(catalogDescriptor: CatalogDescriptor, threadNo: Long): ThreadDescriptor {
      require(threadNo > 0L) { "Bad threadNo: $threadNo" }

      return create(catalogDescriptor.siteKey, catalogDescriptor.boardCode, threadNo)
    }

    fun create(siteKey: SiteKey, boardCode: String, threadNo: Long): ThreadDescriptor {
      require(threadNo > 0L) { "Bad threadNo: $threadNo" }

      return ThreadDescriptor(
        catalogDescriptor = CatalogDescriptor(
          siteKey = siteKey,
          boardCode = boardCode
        ),
        threadNo = threadNo
      )
    }

    fun deserialize(buffer: Buffer): ThreadDescriptor {
      val catalogDescriptor = CatalogDescriptor.deserialize(buffer)
      val threadNo = buffer.readLong()

      return ThreadDescriptor(catalogDescriptor, threadNo)
    }
  }
}

@Immutable
@Parcelize
data class PostDescriptor(
  val threadDescriptor: ThreadDescriptor,
  val postNo: Long,
  val postSubNo: Long = 0L
) : Parcelable, Comparable<PostDescriptor> {

  init {
    check(postNo > 0L) { "Bad postNo: ${postNo}" }
    check(postSubNo >= 0L) { "Bad postSubNo: ${postSubNo}" }
  }

  val siteKeyActual: String
    get() = threadDescriptor.catalogDescriptor.siteKey.key
  val siteKey: SiteKey
    get() = threadDescriptor.catalogDescriptor.siteKey
  val boardCode: String
    get() = threadDescriptor.catalogDescriptor.boardCode
  val threadNo: Long
    get() = threadDescriptor.threadNo
  val catalogDescriptor: CatalogDescriptor
    get() = threadDescriptor.catalogDescriptor
  val isOP: Boolean
    get() = postNo == threadDescriptor.threadNo

  fun serialize(buff: Buffer?): Buffer {
    val buffer = buff ?: Buffer()

    threadDescriptor.serialize(buffer)
    buffer.writeLong(postNo)
    buffer.writeLong(postSubNo)

    return buffer
  }

  override fun compareTo(other: PostDescriptor): Int {
    if (this.threadNo > other.threadNo) {
      return 1
    } else if (this.threadNo < other.threadNo) {
      return -1
    }

    if (this.postNo > other.postNo) {
      return 1
    } else if (this.postNo < other.postNo) {
      return -1
    }

    if (this.postSubNo > other.postSubNo) {
      return 1
    } else if (this.postSubNo < other.postSubNo) {
      return -1
    }

    return 0
  }

  override fun toString(): String {
    return buildString(capacity = 32) {
      append("PostDescriptor(")
      append(siteKeyActual)
      append("/")
      append(boardCode)
      append("/")
      append(threadNo)
      append("/")
      append(postNo)
      append(",")
      append(postSubNo)
      append(")")
    }
  }

  fun asReadableString(): String {
    return buildString(capacity = 32) {
      append(siteKeyActual)
      append("/")
      append(boardCode)
      append("/")
      append(threadNo)
      append("/")
      append(postNo)

      if (postSubNo > 0) {
        append(",")
        append(postSubNo)
      }
    }
  }

  fun asKey(): String {
    return buildString(capacity = 32) {
      append(siteKeyActual)
      append("_")
      append(boardCode)
      append("_")
      append(threadNo)
      append("_")
      append(postNo)

      if (postSubNo > 0) {
        append("_")
        append(postSubNo)
      }
    }
  }

  companion object {

    fun create(
      threadDescriptor: ThreadDescriptor,
      postNo: Long,
      postSubNo: Long = 0L
    ): PostDescriptor {
      return create(
        siteKey = threadDescriptor.siteKey,
        boardCode = threadDescriptor.boardCode,
        threadNo = threadDescriptor.threadNo,
        postNo = postNo,
        postSubNo = postSubNo
      )
    }

    fun create(
      siteKey: SiteKey,
      boardCode: String,
      threadNo: Long,
      postNo: Long,
      postSubNo: Long = 0L
    ): PostDescriptor {
      check(threadNo > 0L) { "Bad threadNo: ${threadNo}" }
      check(postNo > 0L) { "Bad postNo: ${postNo}" }

      return PostDescriptor(
        threadDescriptor = ThreadDescriptor(
          catalogDescriptor = CatalogDescriptor(
            siteKey = siteKey,
            boardCode = boardCode
          ),
          threadNo = threadNo
        ),
        postNo = postNo,
        postSubNo = postSubNo
      )
    }

    fun deserialize(buffer: Buffer): PostDescriptor {
      val threadDescriptor = ThreadDescriptor.deserialize(buffer)
      val postNo = buffer.readLong()
      val postSubNo = buffer.readLong()

      return PostDescriptor(threadDescriptor, postNo, postSubNo)
    }

    fun maxOfPostDescriptors(one: PostDescriptor?, other: PostDescriptor?): PostDescriptor? {
      if (one == null && other == null) {
        return null
      }

      if (one == null || other == null) {
        return one ?: other
      }

      val result = one.compareTo(other)
      if (result > 0) {
        return one
      } else if (result < 0) {
        return other
      }

      return one
    }
  }
}