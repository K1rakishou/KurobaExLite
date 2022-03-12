package com.github.k1rakishou.kurobaexlite.model

abstract class ClientException : Exception {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)
}

class BadStatusResponseException(val status: Int) : ClientException("Bad response status: ${status}") {

  fun isAuthError(): Boolean {
    return status == 401
  }

  fun isForbiddenError(): Boolean {
    return status == 403
  }

  fun isNotFoundError(): Boolean {
    return status == 404
  }

}

class EmptyBodyResponseException : ClientException("Response has no body")