package com.github.k1rakishou.kurobaexlite.ui.helpers.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Dp
import com.github.k1rakishou.kurobaexlite.helpers.ensureSingleMeasurableReturned

@Composable
fun SlotLayout(
  modifier: Modifier = Modifier,
  layoutOrientation: LayoutOrientation,
  builder: SlotBuilder.() -> Unit
) {
  val slots = remember(key1 = builder) {
    with(SlotBuilder()) {
      builder(this)
      build()
    }
  }

  require(slots.isNotEmpty()) { "slots are empty" }

  require(slots.any { slot -> slot is Slot.Fixed }) {
    "At least one slot with fixed size is required " +
      "(slots=${slots.joinToString { it.javaClass.simpleName }})"
  }

  val sum = slots.sumOf { slot -> ((slot as? Slot.Dynamic)?.weight?.toDouble() ?: 0.0) }
  require(1.0 - sum <= 0.01f) {
    "Sum of the Dynamic slot weights must be equal to 1f (sum=${sum})"
  }

  SubcomposeLayout(
    modifier = modifier,
    measurePolicy = { constraints ->
      when (layoutOrientation) {
        LayoutOrientation.Horizontal -> {
          check(constraints.hasBoundedWidth) {
            "Parent's width is unbounded which means it's a scrollable container which is not allowed"
          }
        }
        LayoutOrientation.Vertical -> {
          check(constraints.hasBoundedHeight) {
            "Parent's height is unbounded which means it's a scrollable container which is not allowed"
          }
        }
      }

      val measurables = arrayOfNulls<Measurable>(slots.size)
      val placeables = arrayOfNulls<Placeable>(slots.size)

      for ((index, slot) in slots.withIndex()) {
        val measurable = subcompose(
          slotId = slot.key,
          content = { slot.content() }
        ).ensureSingleMeasurableReturned()

        measurables[index] = measurable
      }

      for ((index, measurable) in measurables.withIndex()) {
        val slot = slots[index]
        if (slot !is Slot.Fixed) {
          continue
        }

        val updatedWidthConstraints = when (layoutOrientation) {
          LayoutOrientation.Horizontal -> {
            constraints.copy(
              minWidth = slot.size.roundToPx(),
              maxWidth = slot.size.roundToPx()
            )
          }
          LayoutOrientation.Vertical -> {
            constraints.copy(
              minHeight = slot.size.roundToPx(),
              maxHeight = slot.size.roundToPx()
            )
          }
        }

        val placeable = measurable!!.measure(updatedWidthConstraints)
        placeables[index] = placeable
      }

      when (layoutOrientation) {
        LayoutOrientation.Horizontal -> {
          val takenWidth = placeables
            .fold(0f) { acc, placeable -> acc + (placeable?.width?.toFloat() ?: 0f ) }

          val remainingWidth = constraints.maxWidth - takenWidth
          check(remainingWidth > 0f) {
            "Bad remainingWidth, maxWidth=${constraints.maxWidth}, " +
              "takenWidth=${takenWidth}, remainingWidth=${remainingWidth}"
          }

          for ((index, measurable) in measurables.withIndex()) {
            val slot = slots[index]
            if (slot !is Slot.Dynamic) {
              continue
            }

            val updatedWidthConstraints = constraints.copy(
              minWidth = (remainingWidth * slot.weight).toInt(),
              maxWidth = (remainingWidth * slot.weight).toInt()
            )

            val placeable = measurable!!.measure(updatedWidthConstraints)
            placeables[index] = placeable
          }
        }
        LayoutOrientation.Vertical -> {
          val takenHeight = placeables
            .fold(0f) { acc, placeable -> acc + (placeable?.height?.toFloat() ?: 0f ) }

          val remainingHeight = constraints.maxHeight - takenHeight
          check(remainingHeight > 0f) {
            "Bad remainingHeight, maxHeight=${constraints.maxHeight}, " +
              "takenHeight=${takenHeight}, remainingHeight=${remainingHeight}"
          }

          for ((index, measurable) in measurables.withIndex()) {
            val slot = slots[index]
            if (slot !is Slot.Dynamic) {
              continue
            }

            val updatedHeightConstraints = constraints.copy(
              minHeight = (remainingHeight * slot.weight).toInt(),
              maxHeight = (remainingHeight * slot.weight).toInt()
            )

            val placeable = measurable!!.measure(updatedHeightConstraints)
            placeables[index] = placeable
          }
        }
      }

      var resultWidth = 0f
      var resultHeight = 0f

      when (layoutOrientation) {
        LayoutOrientation.Horizontal -> {
          // Total sum of children's width
          resultWidth = placeables.fold(0f) { acc, placeable ->
            acc + (placeable?.width?.toFloat() ?: 0f )
          }

          // Max height
          resultHeight = placeables.fold(0f) { acc, placeable ->
            Math.max(acc, placeable?.height?.toFloat() ?: 0f)
          }
        }
        LayoutOrientation.Vertical -> {
          // Max width
          resultWidth = placeables.fold(0f) { acc, placeable ->
            Math.max(acc, placeable?.width?.toFloat() ?: 0f)
          }

          // Total sum of children's height
          resultHeight = placeables.fold(0f) { acc, placeable ->
            acc + (placeable?.height?.toFloat() ?: 0f )
          }
        }
      }

      return@SubcomposeLayout layout(resultWidth.toInt(), resultHeight.toInt()) {
        when (layoutOrientation) {
          LayoutOrientation.Horizontal -> {
            var takenWidth = 0

            for (placeable in placeables) {
              if (placeable == null) {
                continue
              }

              placeable.placeRelative(takenWidth, 0)
              takenWidth += placeable.width
            }
          }
          LayoutOrientation.Vertical -> {
            var takenHeight = 0

            for (placeable in placeables) {
              if (placeable == null) {
                continue
              }

              placeable.placeRelative(0, takenHeight)
              takenHeight += placeable.height
            }
          }
        }
      }
    }
  )
}

class SlotBuilder {
  private val slots = mutableListOf<Slot>()

  fun fixed(
    size: Dp,
    key: Any,
    content: @Composable () -> Unit
  ): SlotBuilder {
    slots += Slot.Fixed(size, key, content)
    return this
  }

  fun dynamic(
    weight: Float,
    key: Any,
    content: @Composable () -> Unit
  ): SlotBuilder {
    slots += Slot.Dynamic(weight, key, content)
    return this
  }

  fun build(): Array<Slot> {
    return slots.toTypedArray()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SlotBuilder

    if (slots != other.slots) return false

    return true
  }

  override fun hashCode(): Int {
    return slots.hashCode()
  }

}

enum class LayoutOrientation {
  Horizontal,
  Vertical
}

sealed class Slot {
  abstract val key: Any
  abstract val content: @Composable () -> Unit

  data class Fixed(
    val size: Dp,
    override val key: Any,
    override val content: @Composable () -> Unit
  ) : Slot()

  data class Dynamic(
    val weight: Float,
    override val key: Any,
    override val content: @Composable () -> Unit
  ) : Slot()
}