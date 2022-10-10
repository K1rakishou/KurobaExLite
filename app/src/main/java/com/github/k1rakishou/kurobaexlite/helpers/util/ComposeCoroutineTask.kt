package com.github.k1rakishou.kurobaexlite.helpers.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface ComposeCoroutineTask {
  val coroutineScope: CoroutineScope
  val isRunning: Boolean

  fun launch(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
  )
}

/**
 * A task where only one block can be executed at a time. Launching a new block while another one is being executed
 * will just do nothing. Good for launching coroutines from button click listeners where you want to be sure that there
 * can be no more than one callback executed at the same time.
 * */
class SingleInstanceTask(
  override val coroutineScope: CoroutineScope
) : ComposeCoroutineTask, RememberObserver {
  @Volatile private var job: Job? = null

  override val isRunning: Boolean
    get() = job != null

  @Synchronized
  override fun launch(
    context: CoroutineContext,
    start: CoroutineStart,
    block: suspend CoroutineScope.() -> Unit
  ) {
    if (job != null) {
      return
    }

    job = coroutineScope.launch(
      context = context,
      start = start,
      block = {
        try {
          block()
        } finally {
          job = null
        }
      }
    )
  }

  @Synchronized
  fun cancel() {
    job?.cancel()
    job = null
  }

  override fun onRemembered() {
  }

  override fun onForgotten() {
    cancel()
  }

  override fun onAbandoned() {
    cancel()
  }

}

@Composable
fun rememberCoroutineTask(taskType: TaskType): ComposeCoroutineTask {
  val coroutineScope = rememberCoroutineScope()

  val composeCoroutineTask by remember {
    val task = when (taskType) {
      TaskType.SingleInstance -> SingleInstanceTask(coroutineScope)
    }

    return@remember mutableStateOf(task)
  }

  return composeCoroutineTask
}

enum class TaskType {
  SingleInstance
}
