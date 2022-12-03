package com.github.k1rakishou.kurobaexlite.helpers.parser

import androidx.compose.runtime.Immutable
import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import com.github.k1rakishou.kurobaexlite.helpers.util.readUtfString
import com.github.k1rakishou.kurobaexlite.helpers.util.writeUtfString
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.themes.ChanThemeColorId
import okio.Buffer

@Immutable
sealed class TextPartSpan {
  val PRIORITY_BACKGROUND = 0
  val PRIORITY_TEXT = 1
  val PRIORITY_FOREGROUND = 2
  val PRIORITY_SPOILER = 1000

  fun priority(): Int {
    return when (this) {
      is BgColor,
      is BgColorId -> PRIORITY_BACKGROUND
      is Linkable.Board,
      is Linkable.Quote,
      is Linkable.Search,
      is Linkable.Url,
      is PartialSpan,
      is Underline -> PRIORITY_TEXT
      is FgColor,
      is FgColorId -> PRIORITY_FOREGROUND
      Spoiler -> PRIORITY_SPOILER
    }
  }

  @Immutable
  class PartialSpan(
    val start: Int,
    val end: Int,
    val linkSpan: TextPartSpan
  ) : TextPartSpan()

  @Immutable
  class BgColor(val color: Int) : TextPartSpan()
  @Immutable
  class FgColor(val color: Int) : TextPartSpan()
  @Immutable
  class BgColorId(val colorId: ChanThemeColorId) : TextPartSpan()
  @Immutable
  class FgColorId(val colorId: ChanThemeColorId) : TextPartSpan()
  @Immutable
  object Spoiler : TextPartSpan()
  @Immutable
  object Underline : TextPartSpan()

  @Immutable
  sealed class Linkable : TextPartSpan() {

    fun serialize(): Buffer {
      val buffer = Buffer()

      buffer.writeInt(id().value)
      serializeLinkable(buffer)

      return buffer
    }

    private fun id(): Id {
      return when (this) {
        is Board -> Id.Board
        is Quote -> Id.Quote
        is Search -> Id.Search
        is Url -> Id.Url
      }
    }

    protected abstract fun serializeLinkable(buffer: Buffer)

    @Immutable
    data class Quote(
      val crossThread: Boolean,
      val dead: Boolean,
      val postDescriptor: PostDescriptor
    ) : Linkable() {
      override fun serializeLinkable(buffer: Buffer) {
        buffer.writeByte(if (crossThread) 1 else 0)
        buffer.writeByte(if (dead) 1 else 0)
        postDescriptor.serialize(buffer)
      }

      companion object {
        fun deserializeLinkable(buffer: Buffer): Quote {
          val crossThread = buffer.readByte() == 1.toByte()
          val dead = buffer.readByte() == 1.toByte()
          val postDescriptor = PostDescriptor.deserialize(buffer)

          return Quote(crossThread, dead, postDescriptor)
        }
      }
    }

    @Immutable
    data class Search(
      val boardCode: String,
      val searchQuery: String
    ) : Linkable() {
      override fun serializeLinkable(buffer: Buffer) {
        buffer.writeUtfString(boardCode)
        buffer.writeUtfString(searchQuery)
      }

      companion object {
        fun deserializeLinkable(buffer: Buffer): Search {
          val boardCode = buffer.readUtfString()
          val searchQuery = buffer.readUtfString()

          return Search(boardCode, searchQuery)
        }
      }
    }

    @Immutable
    data class Board(
      val boardCode: String
    ) : Linkable() {
      override fun serializeLinkable(buffer: Buffer) {
        buffer.writeUtfString(boardCode)
      }

      companion object {
        fun deserializeLinkable(buffer: Buffer): Board {
          val boardCode = buffer.readUtfString()
          return Board(boardCode)
        }
      }
    }

    @Immutable
    data class Url(
      val url: String
    ) : Linkable() {
      override fun serializeLinkable(buffer: Buffer) {
        buffer.writeUtfString(url)
      }

      companion object {
        fun deserializeLinkable(buffer: Buffer): Url {
          val url = buffer.readUtfString()
          return Url(url)
        }
      }
    }

    @Immutable
    enum class Id(val value: Int) {
      Quote(0),
      Search(1),
      Board(2),
      Url(3);

      companion object {
        fun fromValue(value: Int): Id? {
          return when (value) {
            0 -> Quote
            1 -> Search
            2 -> Board
            3 -> Url
            else -> null
          }
        }
      }
    }

    companion object {
      fun deserialize(buffer: Buffer): Linkable? {
        val idValue = buffer.readInt()
        val id = Id.fromValue(idValue)
        if (id == null) {
          logcatError(tag = "Linkable.deserialize()") { "Unknown id: ${idValue}" }
          return null
        }

        return when (id) {
          Id.Quote -> Quote.deserializeLinkable(buffer)
          Id.Search -> Search.deserializeLinkable(buffer)
          Id.Board -> Board.deserializeLinkable(buffer)
          Id.Url -> Url.deserializeLinkable(buffer)
        }
      }
    }

  }

}