package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.helpers.mutableListWithCap
import com.github.k1rakishou.kurobaexlite.model.data.IPostData


data class PostsLoadResult(
  val newPosts: List<IPostData>,
  val updatedPosts: List<IPostData>,
  val unchangedPosts: List<IPostData> = emptyList(),
  val deletedPosts: List<IPostData> = emptyList(),
) {
  val newPostsCount: Int
    get() = newPosts.size
  val updatedPostsCount: Int
    get() = updatedPosts.size
  val newOrUpdatedCount: Int
    get() = newPostsCount + updatedPostsCount

  fun firstOrNull(predicate: (IPostData) -> Boolean): IPostData? {
    iteratePostsWhile { postData ->
      if (predicate(postData)) {
        return postData
      }

      return@iteratePostsWhile true
    }

    return null
  }

  private inline fun iteratePostsWhile(iterator: (IPostData) -> Boolean) {
    for (newPost in newPosts) {
      if (!iterator(newPost)) {
        return
      }
    }

    for (newPost in updatedPosts) {
      if (!iterator(newPost)) {
        return
      }
    }

    for (newPost in unchangedPosts) {
      if (!iterator(newPost)) {
        return
      }
    }
  }

  fun newAndUpdatedCombined(): List<IPostData> {
    val resultList = mutableListWithCap<IPostData>(newPosts.size + updatedPosts.size)

    resultList.addAll(newPosts)
    resultList.addAll(updatedPosts)

    return resultList
  }

  fun allCombined(): List<IPostData> {
    val resultList = mutableListWithCap<IPostData>(newPosts.size + updatedPosts.size + unchangedPosts.size)

    resultList.addAll(newPosts)
    resultList.addAll(updatedPosts)
    resultList.addAll(unchangedPosts)

    return resultList
  }

  fun isEmpty(): Boolean {
    return newPosts.isEmpty() && updatedPosts.isEmpty() && unchangedPosts.isEmpty()
  }

  fun isNotEmpty(): Boolean {
    return !isEmpty()
  }

  fun info(): String {
    return "newPosts=${newPosts.size}, updatedPosts=${updatedPosts.size}, " +
      "unchangedPosts=${unchangedPosts.size}, deletedPosts=${deletedPosts.size}"
  }

  companion object {
    val EMPTY = PostsLoadResult(emptyList(), emptyList(), emptyList(), emptyList())
  }

}