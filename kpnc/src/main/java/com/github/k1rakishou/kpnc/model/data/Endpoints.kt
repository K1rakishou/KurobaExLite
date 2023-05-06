package com.github.k1rakishou.kpnc.model.data

class Endpoints {

  fun getAccountInfoEndpoint(instanceAddress: String): String {
    return "${validate(instanceAddress)}/get_account_info"
  }

  fun updateFirebaseTokenEndpoint(instanceAddress: String): String {
    return "${validate(instanceAddress)}/update_firebase_token"
  }

  fun updateMessageDelivered(instanceAddress: String): String {
    return "${validate(instanceAddress)}/update_message_delivered"
  }

  fun watchPost(instanceAddress: String): String {
    return "${validate(instanceAddress)}/watch_post"
  }

  fun unwatchPost(instanceAddress: String): String {
    return "${validate(instanceAddress)}/unwatch_post"
  }

  private fun validate(instanceAddress: String): String {
    return instanceAddress.removeSuffix("/")
  }

}