package com.github.k1rakishou.kurobaexlite.ui.elements

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.*
import androidx.compose.ui.unit.dp
import com.github.k1rakishou.kurobaexlite.model.data.local.PostData

@Composable
fun PostCell(
  modifier: Modifier = Modifier,
  postData: PostData,
  content: @Composable () -> Unit
) {
  val measurePolicy = createMeasurePolicy(postData)

  Layout(
    modifier = modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .padding(horizontal = 8.dp),
    measurePolicy = measurePolicy,
    content = content
  )
}

@Composable
private fun createMeasurePolicy(postData: PostData): MeasurePolicy {
  return remember(key1 = postData) {
    MeasurePolicy { measurables, constraints ->
      var takenWidth = 0
      var takenHeight = 0
      val placeables = arrayOfNulls<Placeable>(measurables.size)

      for ((index, measurable) in measurables.withIndex()) {
        val placeable = measurable.measure(constraints)
        takenWidth = Math.max(takenWidth, placeable.width)
        takenHeight += placeable.height

        placeables[index] = placeable
      }

      return@MeasurePolicy layout(takenWidth, takenHeight) {
        var offsetY = 0

        for (placeable in placeables) {
          if (placeable == null) {
            return@layout
          }

          placeable.placeRelative(0, offsetY)
          offsetY += placeable.height
        }
      }
    }
  }
}