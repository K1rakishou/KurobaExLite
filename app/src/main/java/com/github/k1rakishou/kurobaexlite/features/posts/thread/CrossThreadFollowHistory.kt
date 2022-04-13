package com.github.k1rakishou.kurobaexlite.features.posts.thread

import com.github.k1rakishou.kurobaexlite.model.descriptors.ThreadDescriptor

class CrossThreadFollowHistory {
  private val history = mutableListOf<ThreadDescriptor>()

  fun push(threadDescriptor: ThreadDescriptor) {
    val indexOfPrevious = history.indexOfFirst { descriptor -> descriptor == threadDescriptor }
    if (indexOfPrevious >= 0) {
      history.add(history.removeAt(indexOfPrevious))
      return
    }

    history.add(threadDescriptor)
  }

  fun peek(): ThreadDescriptor? {
    return history.lastOrNull()
  }

  fun pop(): ThreadDescriptor? {
    return history.removeLastOrNull()
  }

}