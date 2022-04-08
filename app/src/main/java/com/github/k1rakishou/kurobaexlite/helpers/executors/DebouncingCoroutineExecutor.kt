package com.github.k1rakishou.kurobaexlite.helpers.executors

import com.github.k1rakishou.kurobaexlite.helpers.logcatError
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.asLog

@Suppress("JoinDeclarationAndAssignment")
@OptIn(ExperimentalCoroutinesApi::class)
class DebouncingCoroutineExecutor(
  private val scope: CoroutineScope
) {
  private val debouncers = mutableMapOf<String?, Debouncer>()

  private val coroutineExceptionHandler = CoroutineExceptionHandler { coroutineContext, throwable ->
    logcatError(tag = TAG) { "serializedAction unhandled exception, ${throwable.asLog()}" }
    throw RuntimeException(throwable)
  }

  @Synchronized
  fun post(timeout: Long, key: String? = null, func: suspend () -> Unit): Boolean {
    require(timeout > 0L) { "Bad timeout!" }

    val debouncer = debouncers.getOrPut(
      key = key,
      defaultValue = { Debouncer(scope, coroutineExceptionHandler) }
    )

    return debouncer.post(timeout, func)
  }

  @Synchronized
  fun stop(key: String? = null) {
    debouncers.remove(key = key)?.stop()
  }

  @Synchronized
  fun stopAll() {
    debouncers.values.forEach { debouncer -> debouncer.stop() }
    debouncers.clear()
  }

  class Debouncer(
    private val scope: CoroutineScope,
    private val coroutineExceptionHandler: CoroutineExceptionHandler
  ) {
    private val channel = Channel<Payload>(Channel.UNLIMITED)
    private val counter = AtomicLong(0L)
    private val isProgress = AtomicBoolean(false)
    private val channelJob: Job

    init {
      channelJob = scope.launch(coroutineExceptionHandler) {
        var activeJob: Job? = null

        channel.consumeEach { payload ->
          if (counter.get() != payload.id || !isActive || isProgress.get()) {
            return@consumeEach
          }

          activeJob?.cancel()
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

  }

  class Payload(
    val id: Long,
    val timeout: Long,
    val func: suspend () -> Unit
  )

  companion object {
    private const val TAG = "DebouncingCoroutineExecutor"
  }
}