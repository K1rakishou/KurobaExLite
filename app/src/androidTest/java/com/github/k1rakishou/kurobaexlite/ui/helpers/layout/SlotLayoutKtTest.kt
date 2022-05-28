package com.github.k1rakishou.kurobaexlite.ui.helpers.layout

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class SlotLayoutKtTest {
  private val slotLayoutTag = "SlotLayout"

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test(expected = IllegalArgumentException::class)
  fun emptySlotsTestMustCrash() {
    composeTestRule.setContent {
      SlotLayout(
        modifier = Modifier
          .width(200.dp)
          .height(300.dp)
          .testTag(slotLayoutTag),
        layoutOrientation = LayoutOrientation.Horizontal,
        builder = {  }
      )
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun onlyDynamicSlotsTestMustCrash() {
    composeTestRule.setContent {
      SlotLayout(
        modifier = Modifier
          .width(200.dp)
          .height(300.dp)
          .testTag(slotLayoutTag),
        layoutOrientation = LayoutOrientation.Horizontal,
        builder = {
          dynamic(weight = 1f, key = "test", content = {  })
        }
      )
    }
  }

  @Test
  fun testHorizontalLayoutWithMultipleNodes() {
    val slotLayoutWidth = 200.dp
    val slotLayoutHeight = 300.dp

    val dynamicSlotText1 = "Slot.Dynamic1"
    val dynamicSlotText2 = "Slot.Dynamic2"
    val fixedSlotText1 = "Slot.Fixed1"
    val fixedSlotText2 = "Slot.Fixed2"

    composeTestRule.setContent {
      SlotLayout(
        modifier = Modifier
          .width(slotLayoutWidth)
          .height(slotLayoutHeight)
          .testTag(slotLayoutTag),
        layoutOrientation = LayoutOrientation.Horizontal,
        builder = {
          fixed(size = 20.dp, key = fixedSlotText1, content = { SimpleTextHoriz(fixedSlotText1) })
          dynamic(weight = 0.5f, key = dynamicSlotText1, content = { SimpleTextHoriz(dynamicSlotText1) })
          dynamic(weight = 0.5f, key = dynamicSlotText2, content = { SimpleTextHoriz(dynamicSlotText2) })
          fixed(size = 20.dp, key = fixedSlotText2, content = { SimpleTextHoriz(fixedSlotText2) })
        }
      )
    }

    composeTestRule.onNodeWithTag(slotLayoutTag).onChildren().assertCountEquals(4)

    arrayOf(dynamicSlotText1, dynamicSlotText2).forEach { slotText ->
      composeTestRule.onNodeWithText(text = slotText)
        .assert(
          matcher = SemanticsMatcher(
            description = "Ensure dynamic slot has expected size",
            matcher = { semanticsNode ->
              val expectedWidth = with(semanticsNode.layoutInfo.density) { 80.dp.roundToPx() }
              val expectedHeight = with(semanticsNode.layoutInfo.density) { slotLayoutHeight.roundToPx() }

              return@SemanticsMatcher expectedWidth == semanticsNode.layoutInfo.width &&
                expectedHeight == semanticsNode.layoutInfo.height
            }
          )
        )
    }

    arrayOf(fixedSlotText1, fixedSlotText2).forEach { slotText ->
      composeTestRule.onNodeWithText(text = slotText)
        .assert(
          matcher = SemanticsMatcher(
            description = "Ensure fixed slot with text '${slotText}' has expected size",
            matcher = { semanticsNode ->
              val expectedWidth = with(semanticsNode.layoutInfo.density) { 20.dp.roundToPx() }
              val expectedHeight = with(semanticsNode.layoutInfo.density) { slotLayoutHeight.roundToPx() }

              return@SemanticsMatcher expectedWidth == semanticsNode.layoutInfo.width &&
                expectedHeight == semanticsNode.layoutInfo.height
            }
          )
        )
    }
  }

  @Test
  fun testVerticalLayoutWithMultipleNodes() {
    val slotLayoutWidth = 200.dp
    val slotLayoutHeight = 300.dp

    val dynamicSlotText1 = "Slot.Dynamic1"
    val dynamicSlotText2 = "Slot.Dynamic2"
    val fixedSlotText1 = "Slot.Fixed1"
    val fixedSlotText2 = "Slot.Fixed2"

    composeTestRule.setContent {
      SlotLayout(
        modifier = Modifier
          .width(slotLayoutWidth)
          .height(slotLayoutHeight)
          .testTag(slotLayoutTag),
        layoutOrientation = LayoutOrientation.Vertical,
        builder = {
          fixed(size = 20.dp, key = fixedSlotText1, content = { SimpleTextVert(fixedSlotText1) })
          dynamic(weight = 0.5f, key = dynamicSlotText1, content = { SimpleTextVert(dynamicSlotText1) })
          dynamic(weight = 0.5f, key = dynamicSlotText2, content = { SimpleTextVert(dynamicSlotText2) })
          fixed(size = 20.dp, key = fixedSlotText2, content = { SimpleTextVert(fixedSlotText2) })
        }
      )
    }

    composeTestRule.onNodeWithTag(slotLayoutTag).onChildren().assertCountEquals(4)

    arrayOf(dynamicSlotText1, dynamicSlotText2).forEach { slotText ->
      composeTestRule.onNodeWithText(text = slotText)
        .assert(
          matcher = SemanticsMatcher(
            description = "Ensure dynamic slot '${slotText}' has expected size",
            matcher = { semanticsNode ->
              val expectedWidth = with(semanticsNode.layoutInfo.density) { slotLayoutWidth.roundToPx() }
              val expectedHeight = with(semanticsNode.layoutInfo.density) { 130.dp.roundToPx() }

              return@SemanticsMatcher expectedWidth == semanticsNode.layoutInfo.width &&
                expectedHeight == semanticsNode.layoutInfo.height
            }
          )
        )
    }

    arrayOf(fixedSlotText1, fixedSlotText2).forEach { slotText ->
      composeTestRule.onNodeWithText(text = slotText)
        .assert(
          matcher = SemanticsMatcher(
            description = "Ensure fixed slot '${slotText}' has expected size",
            matcher = { semanticsNode ->
              val expectedWidth = with(semanticsNode.layoutInfo.density) { slotLayoutWidth.roundToPx() }
              val expectedHeight = with(semanticsNode.layoutInfo.density) { 20.dp.roundToPx() }

              return@SemanticsMatcher expectedWidth == semanticsNode.layoutInfo.width &&
                expectedHeight == semanticsNode.layoutInfo.height
            }
          )
        )
    }
  }

  @Composable
  private fun SimpleTextHoriz(fixedSlotText1: String) {
    Text(
      modifier = Modifier
        .fillMaxSize()
        .scrollable(state = rememberScrollState(), orientation = Orientation.Horizontal),
      text = fixedSlotText1
    )
  }

  @Composable
  private fun SimpleTextVert(fixedSlotText1: String) {
    Text(
      modifier = Modifier
        .fillMaxSize()
        .scrollable(state = rememberScrollState(), orientation = Orientation.Vertical),
      text = fixedSlotText1
    )
  }


}