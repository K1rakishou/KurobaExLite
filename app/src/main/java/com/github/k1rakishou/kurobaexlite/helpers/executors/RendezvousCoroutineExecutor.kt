package com.github.k1rakishou.kurobaexlite.helpers.executors

import com.github.k1rakishou.kurobaexlite.helpers.util.logcatError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import logcat.asLog

@OptIn(ExperimentalCoroutinesApi::class)
class RendezvousCoroutineExecutor(
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
  private val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    logcatError(tag = TAG) { "serializedAction unhandled exception, ${throwable.asLog()}" }
    throw RuntimeException(throwable)
  }

  private val channel = Channel<SerializedAction>(
    capacity = Channel.RENDEZVOUS,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
  )

  private var job: Job? = null

  init {
    job = scope.launch(context = dispatcher + coroutineExceptionHandler) {
      channel.consumeEach { serializedAction ->
        serializedAction.action()
      }
    }
  }

  fun post(func: suspend () -> Unit) {
    if (channel.isClosedForSend) {
      return
    }

    val serializedAction = SerializedAction(func)
    channel.trySend(serializedAction)
  }

  fun stop() {
    job?.cancel()
    job = null
  }

  data class SerializedAction(
    val action: suspend () -> Unit
  )

  companion object {
    private const val TAG = "RendezvousCoroutineExecutor"
  }
}