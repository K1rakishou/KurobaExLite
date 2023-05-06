package com.github.k1rakishou.kpnc.domain

import com.github.k1rakishou.kpnc.helpers.asLogIfImportantOrErrorMessage
import com.github.k1rakishou.kpnc.helpers.logcatError
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MessageReceiverImpl : MessageReceiver, KoinComponent {
  private val clientAppNotifier: ClientAppNotifier by inject()
  private val serverDeliveryNotifier: ServerDeliveryNotifier by inject()
  private val moshi: Moshi by inject()

  override fun onGotNewMessage(data: String?) {
    if (data.isNullOrEmpty()) {
      logcatError(TAG) { "onGotNewMessage() invalid data='${data?.take(256)}'" }
      return
    }

    val replyMessages = extractReplyMessages(data)
    if (replyMessages.isEmpty()) {
      logcatError(TAG) { "onGotNewMessage() replyMessages is empty" }
      return
    }

    val replyMessageIds = replyMessages.map { it.replyId }
    val postUrl = replyMessages.map { it.newReplyUrl }

    serverDeliveryNotifier.notifyPostUrlsDelivered(replyMessageIds)
      .onFailure { error ->
        logcatError(TAG) { "serverDeliveryNotifier.notifyPostUrlsDelivered() " +
          "error: ${error.asLogIfImportantOrErrorMessage()}" }
      }

    clientAppNotifier.onRepliesReceived(postUrl)
      .onFailure { error ->
        logcatError(TAG) { "clientAppNotifier.onRepliesReceived() " +
          "error: ${error.asLogIfImportantOrErrorMessage()}" }
      }
  }

  private fun extractReplyMessages(data: String): List<FcmReplyMessage> {
    val newFcmRepliesMessage = try {
      moshi.adapter<NewFcmRepliesMessage>(NewFcmRepliesMessage::class.java).fromJson(data)
    } catch (error: Throwable) {
      logcatError(TAG) { "Error while trying to deserialize \'$data\' as NewFcmRepliesMessage" }
      return emptyList()
    }

    if (newFcmRepliesMessage == null) {
      logcatError(TAG) { "Failed to deserialize \'$data\' as NewFcmRepliesMessage" }
      return emptyList()
    }

    return newFcmRepliesMessage.newReplyMessages
  }

  @JsonClass(generateAdapter = true)
  data class NewFcmRepliesMessage(
    @Json(name = "new_reply_messages")
    val newReplyMessages: List<FcmReplyMessage>
  )

  @JsonClass(generateAdapter = true)
  data class FcmReplyMessage(
    @Json(name = "reply_id")
    val replyId: Long,
    @Json(name = "new_reply_url")
    val newReplyUrl: String
  )

  companion object {
    private const val TAG = "MessageProcessorImpl"
  }

}