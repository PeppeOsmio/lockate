package com.peppeosmio.lockate.utils

import com.peppeosmio.lockate.exceptions.AGMemberUnauthorizedException
import com.peppeosmio.lockate.exceptions.APIException
import com.peppeosmio.lockate.exceptions.InvalidApiKeyException
import com.peppeosmio.lockate.exceptions.LocalAGNotFoundException
import com.peppeosmio.lockate.exceptions.RemoteAGNotFoundException
import kotlinx.serialization.SerializationException

data class ErrorDialogInfo(val title: String, val body: String, val exception: Throwable?) {

    override fun toString(): String {
        return "$title: $body"
    }

    companion object {
        fun connectException(e: Throwable): ErrorDialogInfo {
            return ErrorDialogInfo(
                title = "Connection error",
                body = "The server is unreachable. Maybe check your Internet connection?",
                exception = e
            )
        }

        fun serializationException(e: SerializationException): ErrorDialogInfo {
            return ErrorDialogInfo(
                title = "Invalid JSON response", body = e.message ?: "", exception = e
            )
        }

        fun invalidApiKeyException(e: InvalidApiKeyException): ErrorDialogInfo {
            return ErrorDialogInfo(
                title = "Invalid API key",
                body = "The API key you entered before is invalid or expired.",
                exception = e
            )
        }

        fun apiException(e: APIException): ErrorDialogInfo {
            return ErrorDialogInfo(
                title = "Server error", body = "${e.statusCode}: \"${e.body}\"", exception = e
            )
        }

        fun exception(e: Throwable): ErrorDialogInfo {
            return ErrorDialogInfo(
                title = e::class.simpleName ?: "Unknown error",
                body = e.message ?: "no details",
                exception = e
            )
        }

        fun remoteAGNotFoundException(e: RemoteAGNotFoundException): ErrorDialogInfo {
            return ErrorDialogInfo(
                title = "Anonymous group not found",
                body = "This anonymous group doesn't exist.",
                exception = e
            )
        }

        fun notMemberOfAGException(e: AGMemberUnauthorizedException): ErrorDialogInfo {
            return ErrorDialogInfo(
                title = "You're not a member of the group",
                body = "You were removed from this anonymous group by an admin.",
                exception = e
            )
        }

        fun localAGNotFoundException(e: LocalAGNotFoundException): ErrorDialogInfo {
            return ErrorDialogInfo(
                title = "Local AG not found",
                body = "The anonymous group was not found in the local database.",
                exception = e
            )
        }
    }
}