package com.github.k1rakishou.kurobaexlite.model.descriptors

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.helpers.readUtfString
import com.github.k1rakishou.kurobaexlite.helpers.writeUtfString
import kotlinx.parcelize.Parcelize
import okio.Buffer

@Immutable
@Parcelize
inline class SiteKey(val key: String) : Parcelable {
  override fun toString(): String {
    return key
  }
}

@Immutable
sealed class ChanDescriptor {
  abstract val siteKey: SiteKey
  abstract val boardCode: String
  abstract fun asReadableString(): String
}

@Immutable
@Parcelize
data class CatalogDescriptor(
  override val siteKey: SiteKey,
  override val boardCode: String
) : Parcelable, ChanDescriptor() {
  val siteKeyActual: String
    get() = siteKey.key

  fun serialize(buff: Buffer?): Buffer {
    val buffer = buff ?: Buffer()

    buffer.writeUtfString(siteKeyActual)
    buffer.writeUtfString(boardCode)

    return buffer
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
) : Parcelable, ChanDescriptor() {
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
) : Parcelable {
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

  companion object {
    fun create(
      siteKey: SiteKey,
      boardCode: String,
      threadNo: Long,
      postNo: Long,
      postSubNo: Long = 0L
    ): PostDescriptor {
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
  }
}