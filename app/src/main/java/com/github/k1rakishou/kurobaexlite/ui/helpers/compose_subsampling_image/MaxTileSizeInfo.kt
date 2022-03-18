package com.github.k1rakishou.kurobaexlite.ui.helpers.compose_subsampling_image

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntSize

sealed class MaxTileSizeInfo(
  val maxTileSizeState: MutableState<IntSize?>
) {
  val width: Int
    get() = maxTileSizeState.value!!.width
  val height: Int
    get() = maxTileSizeState.value!!.height

  // May crash if the canvas doesn't support width/height
  class Fixed(
    size: IntSize = IntSize(2048, 2048)
  ) : MaxTileSizeInfo(mutableStateOf(size))

  // Will be detected automatically by using Canvas() composable to get the native canvas and then
  // calling getMaximumBitmapWidth/getMaximumBitmapHeight
  class Auto(
    maxTileSizeState: MutableState<IntSize?> = mutableStateOf<IntSize?>(null)
  ) : MaxTileSizeInfo(maxTileSizeState)
}
