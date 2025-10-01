package com.peppeosmio.lockate.utils

import com.peppeosmio.lockate.exceptions.AGMemberUnauthorizedException
import com.peppeosmio.lockate.exceptions.APIException
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.LocalAGNotFoundException
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import kotlinx.serialization.SerializationException
import java.net.ConnectException
import java.net.NoRouteToHostException

data class ErrorInfo(val title: String, val body: String, val exception: Throwable?) {

    override fun toString(): String {
        return "$title: $body"
    }

    companion object {
        fun fromException(e: Throwable): ErrorInfo {
            when (e) {
                is RemoteAGNotFoundException -> return ErrorInfo(
                    title = "Anonymous group not found",
                    body = "This anonymous group doesn't exist.",
                    exception = e
                )

                is LocalAGNotFoundException -> return ErrorInfo(
                    title = "Local AG not found",
                    body = "The anonymous group was not found in the local database.",
                    exception = e
                )

                is ConnectException, is NoRouteToHostException -> return ErrorInfo(
                    title = "Connection error",
                    body = "The server is unreachable. Maybe check your Internet connection?",
                    exception = e
                )

                is SerializationException -> return ErrorInfo(
                    title = "Invalid JSON response", body = e.message ?: "", exception = e
                )

                is InvalidApiKeyException -> return ErrorInfo(
                    title = "Invalid API key",
                    body = "The API key you entered before is invalid or expired.",
                    exception = e
                )

                is AGMemberUnauthorizedException -> return ErrorInfo(
                    title = "You're not a member of the group",
                    body = "You were removed from this anonymous group by an admin.",
                    exception = e
                )

                is APIException -> return ErrorInfo(
                    title = "Server error", body = "${e.statusCode}: \"${e.body}\"", exception = e
                )

                else -> {
                    return ErrorInfo(
                        title = e::class.simpleName ?: "Unknown error",
                        body = e.message ?: "no details",
                        exception = e
                    )
                }
            }
        }

        fun connectException(e: Throwable): ErrorInfo {
            return ErrorInfo(
                title = "Connection error",
                body = "The server is unreachable. Maybe check your Internet connection?",
                exception = e
            )
        }

        fun serializationException(e: SerializationException): ErrorInfo {
            return ErrorInfo(
                title = "Invalid JSON response", body = e.message ?: "", exception = e
            )
        }

        fun invalidApiKeyException(e: InvalidApiKeyException): ErrorInfo {
            return ErrorInfo(
                title = "Invalid API key",
                body = "The API key you entered before is invalid or expired.",
                exception = e
            )
        }

        fun apiException(e: APIException): ErrorInfo {
            return ErrorInfo(
                title = "Server error", body = "${e.statusCode}: \"${e.body}\"", exception = e
            )
        }

        fun exception(e: Throwable): ErrorInfo {
            return ErrorInfo(
                title = e::class.simpleName ?: "Unknown error",
                body = e.message ?: "no details",
                exception = e
            )
        }

        fun remoteAGNotFoundException(e: RemoteAGNotFoundException): ErrorInfo {
            return ErrorInfo(
                title = "Anonymous group not found",
                body = "This anonymous group doesn't exist.",
                exception = e
            )
        }

        fun notMemberOfAGException(e: AGMemberUnauthorizedException): ErrorInfo {
            return ErrorInfo(
                title = "You're not a member of the group",
                body = "You were removed from this anonymous group by an admin.",
                exception = e
            )
        }

        fun localAGNotFoundException(e: LocalAGNotFoundException): ErrorInfo {
            return ErrorInfo(
                title = "Local AG not found",
                body = "The anonymous group was not found in the local database.",
                exception = e
            )
        }
    }
}