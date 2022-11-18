package com.github.k1rakishou.kurobaexlite.features.posts.thread

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor
import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor
import kotlin.math.absoluteValue

class PostFollowStack {
  private val history = mutableListOf<Entry>()

  private val _isHistoryEmpty = mutableStateOf<Boolean>(true)
  val isHistoryEmpty: State<Boolean>
    get() = _isHistoryEmpty

  private var currentScrollDelta = 0f

  fun push(threadDescriptor: ThreadDescriptor) {
    push(Entry.Thread(threadDescriptor))
  }

  fun push(postDescriptor: PostDescriptor,  popupInfo: Entry.PopupInfo? = null) {
    push(Entry.Post(postDescriptor, popupInfo))
  }

  private fun push(entry: Entry) {
    val indexOfPrevious = history.indexOfFirst { entryFromHistory -> entry == entryFromHistory }
    if (indexOfPrevious >= 0) {
      history.add(history.removeAt(indexOfPrevious))
    } else {
      history.add(entry)
    }

    _isHistoryEmpty.value = history.isEmpty()
  }

  fun pop(): Entry? {
    val entry = history.removeLastOrNull()
    _isHistoryEmpty.value = history.isEmpty()

    return entry
  }

  fun onPostListScrolled(delta: Float) {
    if (history.isEmpty()) {
      return
    }

    currentScrollDelta += delta

    if (currentScrollDelta.absoluteValue > 1000f) {
      history.clear()
      _isHistoryEmpty.value = true
      currentScrollDelta = 0f
    }
  }

  sealed interface Entry {

    data class Thread(
      val threadDescriptor: ThreadDescriptor
    ) : Entry {

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Thread

        if (threadDescriptor != other.threadDescriptor) return false

        return true
      }

      override fun hashCode(): Int {
        return threadDescriptor.hashCode()
      }

    }

    data class Post(
      val postDescriptor: PostDescriptor,
      val popupInfo: PopupInfo?
    ) : Entry {

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Post

        if (postDescriptor != other.postDescriptor) return false

        return true
      }

      override fun hashCode(): Int {
        return postDescriptor.hashCode()
      }

    }

    data class PopupInfo(
      val type: Type
    ) {

      enum class Type {
        RepliesFrom,
        ReplyTo
      }
    }

  }

}