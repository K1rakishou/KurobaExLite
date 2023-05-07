package com.github.k1rakishou.kpnc.model

internal abstract class ClientException : Exception {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)
}

internal class GenericClientException(message: String) : ClientException(message)

internal class ServerErrorException(message: String) : ClientException(message)

internal class JsonConversionException(message: String) : ClientException(message)

internal class BadStatusResponseException(val status: Int) : ClientException("Bad response status: ${status}")

internal class EmptyBodyResponseException : ClientException("Response has no body")