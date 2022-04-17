package com.github.k1rakishou.kurobaexlite.model.data.local

import com.github.k1rakishou.kurobaexlite.model.descriptors.PostDescriptor

data class MarkedPost(
  val postDescriptor: PostDescriptor,
  val markedPostType: MarkedPostType
)

enum class MarkedPostType(val type: Int) {
  MyPost(0);

  companion object {
    fun fromTypRaw(value: Int): MarkedPostType? {
      return MarkedPostType.values()
        .firstOrNull { markedPostType -> markedPostType.type == value }
    }
  }
}