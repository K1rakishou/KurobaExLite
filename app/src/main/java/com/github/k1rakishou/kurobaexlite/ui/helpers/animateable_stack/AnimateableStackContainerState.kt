package com.github.k1rakishou.kurobaexlite.ui.helpers.animateable_stack

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.github.k1rakishou.kurobaexlite.helpers.removeIfKt

interface DisposableElement {
  fun onCreate()
  fun onDispose()
}

abstract class StackContainerElementWrapper<T : DisposableElement>(
  val element: T
) {
  abstract val key: Any
}

class SimpleStackContainerElement<T : DisposableElement>(
  element: T,
  private val keyExtractor: (T) -> Any
) : StackContainerElementWrapper<T>(element) {
  private val _key
    get() = keyExtractor(element)

  override val key: Any = _key
}

@Stable
class AnimateableStackContainerState<T : DisposableElement>(
  initialValues: List<StackContainerElementWrapper<T>>,
  private val key: Any,
  private val _addedElementWrappers: SnapshotStateList<StackContainerChange<T>> = mutableStateListOf(),
  private val _animatingChanges: SnapshotStateList<StackContainerChange<T>> = mutableStateListOf()
) {
  val addedElementWrappers: List<StackContainerElementWrapper<T>>
    get() = _addedElementWrappers.map { it.elementWrapper }
  val animatingChanges: List<StackContainerChange<T>>
    get() = _animatingChanges
  val addedElementsCount: Int
    get() = _addedElementWrappers.size

  init {
    initialValues.forEach { initialValue ->
      _addedElementWrappers += StackContainerChange(initialValue, StackContainerAnimation.Set)
      initialValue.element.onCreate()
    }
  }

  fun setIfEmpty(elementWrapper: StackContainerElementWrapper<T>) {
    if (_addedElementWrappers.isNotEmpty()) {
      return
    }

    removeExistingElement(elementWrapper)
    _animatingChanges.clear()

    _addedElementWrappers += StackContainerChange(elementWrapper, StackContainerAnimation.Set)
    elementWrapper.element.onCreate()
  }

  fun fadeIn(elementWrapper: StackContainerElementWrapper<T>) {
    removeExistingElement(elementWrapper)
    _animatingChanges.clear()

    val lastIndex = _addedElementWrappers.lastIndex
    if (lastIndex >= 0) {
      val topElementWrapper = _addedElementWrappers.get(lastIndex).elementWrapper
      val fadeOut = StackContainerChange(
        elementWrapper = topElementWrapper,
        animation = StackContainerAnimation.Fade(
          fadeType = StackContainerAnimation.FadeType.Out(isDisposing = false)
        )
      )

      _addedElementWrappers.removeAt(lastIndex)
      _animatingChanges.add(fadeOut)
    }

    val fadeIn = StackContainerChange(
      elementWrapper = elementWrapper,
      animation = StackContainerAnimation.Fade(
        fadeType = StackContainerAnimation.FadeType.In
      )
    )

    _animatingChanges.add(fadeIn)
    elementWrapper.element.onCreate()
  }

  fun push(elementWrapper: StackContainerElementWrapper<T>) {
    removeExistingElement(elementWrapper)
    _animatingChanges.clear()

    val push = StackContainerChange(elementWrapper, StackContainerAnimation.Push)
    _animatingChanges.add(push)
    elementWrapper.element.onCreate()
  }

  fun popAll() {
    _animatingChanges.clear()

    while (addedElementWrappers.isNotEmpty()) {
      removeTop(withAnimation = false)
    }
  }

  fun removeTop(
    withAnimation: Boolean = true,
    predicate: (Any) -> Boolean = { true }
  ): Boolean {
    val topChange = _addedElementWrappers.lastOrNull()
      ?: return false

    if (!predicate(topChange.elementWrapper.key)) {
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
    val toRemove = _addedElementWrappers
      .firstOrNull { stackContainerChange -> stackContainerChange.elementWrapper.key == key }
      ?: return false

    return remove(toRemove, withAnimation)
  }

  fun remove(
    stackContainerChange: StackContainerChange<T>,
    withAnimation: Boolean = true
  ): Boolean {
    if (!withAnimation) {
      val removed = _addedElementWrappers.removeIfKt { it.elementWrapper.key == stackContainerChange.elementWrapper.key }
      if (removed) {
        stackContainerChange.elementWrapper.element.onDispose()
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
          elementWrapper = stackContainerChange.elementWrapper,
          animation = StackContainerAnimation.Pop
        )

        _animatingChanges.add(pop)
        return true
      }
      is StackContainerAnimation.Remove -> {
        // already removed
        return false
      }
      is StackContainerAnimation.Set -> {
        _addedElementWrappers
          .removeIfKt { it.elementWrapper.key == stackContainerChange.elementWrapper.key }
        return true
      }
      is StackContainerAnimation.Fade -> {
        when (animation.fadeType) {
          StackContainerAnimation.FadeType.In -> {
            val penultimateIndex = _addedElementWrappers.lastIndex - 1
            if (penultimateIndex >= 0) {
              val penultimateElementWrapper = _addedElementWrappers.get(penultimateIndex).elementWrapper
              val fadeIn = StackContainerChange(
                elementWrapper = penultimateElementWrapper,
                animation = StackContainerAnimation.Fade(StackContainerAnimation.FadeType.In)
              )

              _addedElementWrappers.removeIfKt { it.elementWrapper.key == penultimateElementWrapper.key }
              _animatingChanges.add(fadeIn)
            }

            val fadeOut = StackContainerChange(
              elementWrapper = stackContainerChange.elementWrapper,
              animation = StackContainerAnimation.Fade(
                StackContainerAnimation.FadeType.Out(isDisposing = true)
              )
            )

            _addedElementWrappers.removeIfKt { it.elementWrapper.key == stackContainerChange.elementWrapper.key }
            _animatingChanges.add(fadeOut)

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
    if (animatingChanges.isEmpty()) {
      return
    }

    animatingChanges
      .filter { change -> change.animation.isDisposing }
      .forEach { disposingElement -> disposingElement.elementWrapper.element.onDispose() }

    for (animatingChange in animatingChanges) {
      when (val animation = animatingChange.animation) {
        is StackContainerAnimation.Fade -> {
          when (animation.fadeType) {
            is StackContainerAnimation.FadeType.In -> {
              _addedElementWrappers.add(animatingChange)
            }
            is StackContainerAnimation.FadeType.Out -> {
              if (animation.fadeType.isDisposing) {
                _addedElementWrappers
                  .removeIfKt { it.elementWrapper.key == animatingChange.elementWrapper.key }
              } else {
                _addedElementWrappers.add(animatingChange)
              }
            }
          }
        }
        is StackContainerAnimation.Pop -> {
          _addedElementWrappers
            .removeIfKt { it.elementWrapper.key == animatingChange.elementWrapper.key }
        }
        is StackContainerAnimation.Push -> {
          _addedElementWrappers.add(animatingChange)
        }
        is StackContainerAnimation.Remove -> {
          _addedElementWrappers
            .removeIfKt { it.elementWrapper.key == animatingChange.elementWrapper.key }
        }
        is StackContainerAnimation.Set -> {
          _addedElementWrappers.add(animatingChange)
        }
      }
    }

    _animatingChanges.clear()
  }

  private fun removeExistingElement(elementWrapper: StackContainerElementWrapper<T>) {
    val existingElementIndex = _addedElementWrappers.indexOfFirst { stackContainerChange ->
      stackContainerChange.elementWrapper.key == elementWrapper.key
    }

    if (existingElementIndex >= 0) {
      _addedElementWrappers.removeAt(existingElementIndex)
    }
  }

}

data class StackContainerChange<T : DisposableElement>(
  val elementWrapper: StackContainerElementWrapper<T>,
  val animation: StackContainerAnimation
)

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
