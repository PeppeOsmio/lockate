package com.peppeosmio.lockate.service.invite

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * What an invite's QR code holds: the server, the code and, when the server asks for one, the
 * inviter's API key, which the invitee then uses too. Copy and Share give only the code: the key
 * travels only in the QR, shown in person.
 */
class InviteLink(val server: String, val code: InviteCode, val apiKey: String?) {

    fun toUri(): String = buildString {
        append("$SCHEME://$HOST?server=").append(encode(server))
        append("&code=").append(encode(code.toString()))
        apiKey?.let { append("&key=").append(encode(it)) }
    }

    companion object {
        private const val SCHEME = "lockate"
        private const val HOST = "invite"

        /** The link a scanned QR code reads as, or null when it isn't a Lockate invite */
        fun parse(text: String): InviteLink? {
            val uri =
                try {
                    URI(text.trim())
                } catch (_: URISyntaxException) {
                    return null
                }
            if (uri.scheme != SCHEME || uri.host != HOST) return null
            val params =
                uri.rawQuery
                    ?.split('&')
                    ?.mapNotNull { pair ->
                        val parts = pair.split('=', limit = 2)
                        if (parts.size == 2) parts[0] to decode(parts[1]) else null
                    }
                    ?.toMap() ?: return null
            val server = params["server"]?.takeIf { it.isNotBlank() } ?: return null
            val code = params["code"]?.let { InviteCode.parse(it) } ?: return null
            return InviteLink(server, code, params["key"]?.takeIf { it.isNotBlank() })
        }

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

        private fun decode(value: String) = URLDecoder.decode(value, "UTF-8")
    }
}
