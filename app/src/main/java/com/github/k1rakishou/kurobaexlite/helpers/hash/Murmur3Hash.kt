package com.github.k1rakishou.kurobaexlite.helpers.hash

import androidx.compose.runtime.Immutable

/** 128 bits of state  */
@Immutable
data class Murmur3Hash(
  val val1: Long,
  val val2: Long
) {
  fun combine(other: Murmur3Hash): Murmur3Hash {
    return Murmur3Hash(val1 xor other.val1, val2 xor other.val2)
  }

  fun copy(): Murmur3Hash {
    return Murmur3Hash(val1, val2)
  }

  companion object {
    @JvmField
    val EMPTY = Murmur3Hash(0L, 0L)
  }
}
