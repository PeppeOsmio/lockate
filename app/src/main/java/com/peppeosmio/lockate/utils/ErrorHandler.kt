package com.peppeosmio.lockate.utils

import android.util.Log
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
        customHandler: ((e: Throwable) -> ErrorDialogInfo?)? = null, callback: suspend () -> T
    ): ResultWithError<T> {
        try {
            try {
                return ResultWithError(value = callback(), errorDialogInfo = null)
            } catch (e: Exception) {
                if (customHandler != null) {
                    return ResultWithError(value = null, errorDialogInfo = customHandler(e))
                }
                throw e
            }
        } catch (e: Throwable) {
            val errorDialogInfo = when (e) {
                is RemoteAGNotFoundException -> ErrorDialogInfo.remoteAGNotFoundException(e)
                is LocalAGNotFoundException -> ErrorDialogInfo.localAGNotFoundException(e)
                is ConnectException, is NoRouteToHostException -> ErrorDialogInfo.connectException(e)
                is SerializationException -> ErrorDialogInfo.serializationException(e)
                is InvalidApiKeyException -> ErrorDialogInfo.invalidApiKeyException(e)
                is AGMemberUnauthorizedException -> ErrorDialogInfo.notMemberOfAGException(e)
                is APIException -> ErrorDialogInfo.apiException(e)
                else -> {
                    e.printStackTrace()
                    ErrorDialogInfo.exception(e)
                }
            }
            return ResultWithError(value = null, errorDialogInfo = errorDialogInfo)
        }
    }
}
