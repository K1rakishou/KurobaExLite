package com.github.k1rakishou.kurobaexlite.helpers.executors

import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import logcat.asLog

@OptIn(ExperimentalCoroutinesApi::class)
class RendezvousCoroutineExecutor(
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
  private val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
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
        try {
          serializedAction.action()
        } catch (error: Throwable) {
          logcatError(tag = TAG) { "serializedAction unhandled exception, ${error.asLog()}" }
        }
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