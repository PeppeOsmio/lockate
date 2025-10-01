package com.peppeosmio.lockate.utils

import com.peppeosmio.lockate.data.anonymous_group.remote.ErrorResponseDto
import com.peppeosmio.lockate.exceptions.AGMemberUnauthorizedException
import com.peppeosmio.lockate.exceptions.APIException
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.LocalAGNotFoundException
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import com.peppeosmio.lockate.exceptions.UnauthorizedException
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerializationException
import java.net.ConnectException
import java.net.NoRouteToHostException

object ErrorHandler {
    @Throws(InvalidApiKeyException::class, UnauthorizedException::class)
    suspend fun handleUnauthorized(response: HttpResponse) {
        try {
            val errorResponseBody = response.body<ErrorResponseDto>()
            if (errorResponseBody.error == "invalid_api_key" || errorResponseBody.error == "missing_api_key") {
                throw InvalidApiKeyException()
            } else {
                throw UnauthorizedException()
            }
        } catch (e: SerializationException) {
            throw UnauthorizedException();
        }
    }

    @Throws(APIException::class)
    suspend fun handleGeneric(response: HttpResponse) {
        var body: String
        try {
            val errorResponseBody = response.body<ErrorResponseDto>()
            body = errorResponseBody.error
        } catch (e: SerializationException) {
            body = response.bodyAsText()
        }
        throw APIException(
            statusCode = response.status.value, body = body
        )
    }

    /**
     * @param customHandler: a function that returns an `ErrorInfo` for the exceptions it must handle
     * and re-throws `e` to let it propagate and be handled by the other default handlers
     */
    suspend fun <T> runAndHandleException(
        customHandler: ((e: Throwable) -> ErrorInfo?)? = null, callback: suspend () -> T
    ): ResultWithError<T> {
        try {
            try {
                return ResultWithError(value = callback(), errorInfo = null)
            } catch (e: Exception) {
                if (customHandler != null) {
                    return ResultWithError(value = null, errorInfo = customHandler(e))
                }
                throw e
            }
        } catch (e: Throwable) {
            val errorInfo = when (e) {
                is RemoteAGNotFoundException -> ErrorInfo.remoteAGNotFoundException(e)
                is LocalAGNotFoundException -> ErrorInfo.localAGNotFoundException(e)
                is ConnectException, is NoRouteToHostException -> ErrorInfo.connectException(e)
                is SerializationException -> ErrorInfo.serializationException(e)
                is InvalidApiKeyException -> ErrorInfo.invalidApiKeyException(e)
                is AGMemberUnauthorizedException -> ErrorInfo.notMemberOfAGException(e)
                is APIException -> ErrorInfo.apiException(e)
                else -> {
                    e.printStackTrace()
                    ErrorInfo.exception(e)
                }
            }
            return ResultWithError(value = null, errorInfo = errorInfo)
        }
    }
}
