package com.github.k1rakishou.kurobaexlite.helpers.executors

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Suppress("JoinDeclarationAndAssignment")
@OptIn(ExperimentalCoroutinesApi::class)
class DebouncingCoroutineExecutor(
  scope: CoroutineScope
) {
  private val channel = Channel<Payload>(Channel.UNLIMITED)
  private val counter = AtomicLong(0L)
  private val isProgress = AtomicBoolean(false)
  private val channelJob: Job

  private val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    throw RuntimeException(throwable)
  }

  init {
    channelJob = scope.launch(coroutineExceptionHandler) {
      var activeJob: Job? = null

      channel.consumeEach { payload ->
        if (counter.get() != payload.id || !isActive || isProgress.get()) {
          return@consumeEach
        }

        activeJob?.cancel()
        activeJob = null

        activeJob = scope.launch {
          delay(payload.timeout)

          if (counter.get() != payload.id || !isActive) {
            return@launch
          }

          if (!isProgress.compareAndSet(false, true)) {
            return@launch
          }

          try {
            payload.func.invoke()
          } finally {
            isProgress.set(false)
          }
        }
      }
    }
  }

  fun post(timeout: Long, func: suspend () -> Unit): Boolean {
    require(timeout > 0L) { "Bad timeout!" }

    if (channel.isClosedForSend) {
      return false
    }

    return channel.trySend(Payload(counter.incrementAndGet(), timeout, func)).isSuccess
  }

  // For tests. Most of the time you don't really need to call this.
  fun stop() {
    channelJob.cancel()
  }

  class Payload(
    val id: Long,
    val timeout: Long,
    val func: suspend () -> Unit
  )
}