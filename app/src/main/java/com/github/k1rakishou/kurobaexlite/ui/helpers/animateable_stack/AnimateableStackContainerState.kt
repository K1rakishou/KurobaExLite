package com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.helpers.removeIfKt

interface DisposableElement {
  val elementKey: String

  fun onCreate()
  fun onDispose()
}

class SimpleStackContainerElement<T : DisposableElement>(
  val element: T
) {
  val elementKey: String
    get() = element.elementKey
}

@Stable
class AnimateableStackContainerState<T : DisposableElement>(
  private val _addedElements: SnapshotStateList<StackContainerChange<T>> = mutableStateListOf(),
  private val _animatingElements: SnapshotStateList<StackContainerChange<T>> = mutableStateListOf()
) {
  val addedElements: List<StackContainerChange<T>>
    get() = _addedElements
  val animatingElements: List<StackContainerChange<T>>
    get() = _animatingElements
  val addedElementsCount: Int
    get() = _addedElements.size

  fun contains(elementKey: String): Boolean {
    val containsInAdded = addedElements.indexOfFirst { it.elementKey == elementKey } >= 0
    if (containsInAdded) {
      return true
    }

    val indexInAnimating = animatingElements.indexOfFirst { it.elementKey == elementKey }
    if (indexInAnimating < 0) {
      return false
    }

    val stackContainerChange = animatingElements[indexInAnimating]
    if (stackContainerChange.animation.isDisposing) {
      return false
    }

    return true
  }

  fun set(stackContainerElement: SimpleStackContainerElement<T>, onlyIfEmpty: Boolean) {
    if (onlyIfEmpty && _addedElements.isNotEmpty()) {
      return
    }

    removeExistingElement(stackContainerElement)
    _animatingElements.clear()

    _addedElements += StackContainerChange(stackContainerElement, StackContainerAnimation.Set)
    stackContainerElement.element.onCreate()
  }

  fun fadeIn(stackContainerElement: SimpleStackContainerElement<T>) {
    removeExistingElement(stackContainerElement)
    _animatingElements.clear()

    val lastIndex = _addedElements.lastIndex
    if (lastIndex >= 0) {
      val topElement = _addedElements.get(lastIndex).stackContainerElement
      val fadeOut = StackContainerChange(
        stackContainerElement = topElement,
        animation = StackContainerAnimation.Fade(
          fadeType = StackContainerAnimation.FadeType.Out(isDisposing = false)
        )
      )

      _addedElements.removeAt(lastIndex)
      _animatingElements.add(fadeOut)
    }

    val fadeIn = StackContainerChange(
      stackContainerElement = stackContainerElement,
      animation = StackContainerAnimation.Fade(
        fadeType = StackContainerAnimation.FadeType.In
      )
    )

    _animatingElements.add(fadeIn)
    stackContainerElement.element.onCreate()
  }

  fun push(stackContainerElement: SimpleStackContainerElement<T>) {
    removeExistingElement(stackContainerElement)
    _animatingElements.clear()

    val push = StackContainerChange(stackContainerElement, StackContainerAnimation.Push)
    _animatingElements.add(push)
    stackContainerElement.element.onCreate()
  }

  fun popAll() {
    _animatingElements.clear()

    while (addedElements.isNotEmpty()) {
      removeTop(withAnimation = false)
    }
  }

  fun removeTop(
    withAnimation: Boolean = true,
    predicate: (Any) -> Boolean = { true }
  ): Boolean {
    val topChange = _addedElements.lastOrNull()
      ?: return false

    if (!predicate(topChange.elementKey)) {
      return false
    }

    return remove(
      stackContainerChange = topChange,
      withAnimation = withAnimation
    )
  }

  fun removeByKey(
    key: Any,
    withAnimation: Boolean = true
  ): Boolean {
    val toRemove = _addedElements
      .firstOrNull { stackContainerChange -> stackContainerChange.elementKey == key }
      ?: return false

    return remove(toRemove, withAnimation)
  }

  fun remove(
    stackContainerChange: StackContainerChange<T>,
    withAnimation: Boolean = true
  ): Boolean {
    if (!withAnimation) {
      val removed = _addedElements.removeIfKt { it.elementKey == stackContainerChange.elementKey }
      if (removed) {
        stackContainerChange.stackContainerElement.element.onDispose()
      }

      return true
    }

    when (val animation = stackContainerChange.animation) {
      is StackContainerAnimation.Pop -> {
        // already removed
        return false
      }
      is StackContainerAnimation.Push -> {
        val pop = StackContainerChange(
          stackContainerElement = stackContainerChange.stackContainerElement,
          animation = StackContainerAnimation.Pop
        )

        _animatingElements.add(pop)
        return true
      }
      is StackContainerAnimation.Remove -> {
        // already removed
        return false
      }
      is StackContainerAnimation.Set -> {
        val indexOfElement = _addedElements
          .indexOfFirst { it.elementKey == stackContainerChange.elementKey }

        if (indexOfElement >= 0) {
          val stackContainerChange = _addedElements.removeAt(indexOfElement)
          stackContainerChange.stackContainerElement.element.onDispose()
        }

        return true
      }
      is StackContainerAnimation.Fade -> {
        when (animation.fadeType) {
          StackContainerAnimation.FadeType.In -> {
            val penultimateIndex = _addedElements.lastIndex - 1
            if (penultimateIndex >= 0) {
              val penultimateElement = _addedElements.get(penultimateIndex).stackContainerElement
              val fadeIn = StackContainerChange(
                stackContainerElement = penultimateElement,
                animation = StackContainerAnimation.Fade(StackContainerAnimation.FadeType.In)
              )

              _addedElements.removeIfKt { it.elementKey == penultimateElement.element.elementKey }
              _animatingElements.add(fadeIn)
            }

            val fadeOut = StackContainerChange(
              stackContainerElement = stackContainerChange.stackContainerElement,
              animation = StackContainerAnimation.Fade(
                StackContainerAnimation.FadeType.Out(isDisposing = true)
              )
            )

            _addedElements.removeIfKt { it.elementKey == stackContainerChange.elementKey }
            _animatingElements.add(fadeOut)

            return true
          }
          is StackContainerAnimation.FadeType.Out -> {
            // already removed
            return false
          }
        }
      }
    }
  }

  internal fun onAnimationFinished() {
    if (animatingElements.isEmpty()) {
      return
    }

    animatingElements
      .filter { change -> change.animation.isDisposing }
      .forEach { disposingElement -> disposingElement.stackContainerElement.element.onDispose() }

    for (animatingChange in animatingElements) {
      when (val animation = animatingChange.animation) {
        is StackContainerAnimation.Fade -> {
          when (animation.fadeType) {
            is StackContainerAnimation.FadeType.In -> {
              _addedElements.add(animatingChange)
            }
            is StackContainerAnimation.FadeType.Out -> {
              if (animation.fadeType.isDisposing) {
                _addedElements
                  .removeIfKt { it.elementKey == animatingChange.elementKey }
              } else {
                _addedElements.add(animatingChange)
              }
            }
          }
        }
        is StackContainerAnimation.Pop -> {
          _addedElements
            .removeIfKt { it.elementKey == animatingChange.elementKey }
        }
        is StackContainerAnimation.Push -> {
          _addedElements.add(animatingChange)
        }
        is StackContainerAnimation.Remove -> {
          _addedElements
            .removeIfKt { it.elementKey == animatingChange.elementKey }
        }
        is StackContainerAnimation.Set -> {
          _addedElements.add(animatingChange)
        }
      }
    }

    _animatingElements.clear()
  }

  private fun removeExistingElement(stackContainerElement: SimpleStackContainerElement<T>) {
    val existingElementIndex = _addedElements.indexOfFirst { stackContainerChange ->
      stackContainerChange.elementKey == stackContainerElement.element.elementKey
    }

    if (existingElementIndex >= 0) {
      _addedElements.removeAt(existingElementIndex)
    }
  }

}

data class StackContainerChange<T : DisposableElement>(
  val stackContainerElement: SimpleStackContainerElement<T>,
  val animation: StackContainerAnimation
) {
  val elementKey: String
    get() = stackContainerElement.element.elementKey
}

sealed class StackContainerAnimation {

  val isDisposing: Boolean
    get() {
      return when (this) {
        is Fade -> fadeType is FadeType.Out
        is Pop -> true
        is Push -> false
        is Remove -> true
        is Set -> false
      }
    }

  object Set : StackContainerAnimation() {
    override fun toString(): String = "Set"
  }

  object Remove : StackContainerAnimation() {
    override fun toString(): String = "Remove"
  }

  object Push : StackContainerAnimation() {
    override fun toString(): String = "Push"
  }

  object Pop : StackContainerAnimation() {
    override fun toString(): String = "Pop"
  }

  data class Fade(val fadeType: FadeType) : StackContainerAnimation() {
    override fun toString(): String = "Fade(fadeType=$fadeType)"
  }

  sealed class FadeType {
    object In : FadeType() {
      override fun toString(): String = "In"
    }

    data class Out(val isDisposing: Boolean) : FadeType() {
      override fun toString(): String = "Out(isDisposing=$isDisposing))"
    }
  }

}
