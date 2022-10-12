package com.github.k1rakishou.kurobaexlite.helpers.util

import java.io.Serializable

public data class Tuple3<out A, out B, out C>(
  public val first: A,
  public val second: B,
  public val third: C
) : Serializable {

  public override fun toString(): String = "($first, $second, $third)"
}

public data class Tuple4<out A, out B, out C, out D>(
  public val first: A,
  public val second: B,
  public val third: C,
  public val fourth: D,
) : Serializable {

  public override fun toString(): String = "($first, $second, $third, $fourth)"
}