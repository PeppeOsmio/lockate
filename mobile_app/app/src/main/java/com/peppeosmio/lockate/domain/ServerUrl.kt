package com.peppeosmio.lockate.domain

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

object ServerUrl {
    /**
     * The one form a server's address is saved in, so the same server can't be saved twice: scheme
     * and host in lower case, no default port, no trailing slash. An address that isn't a URL is
     * only trimmed.
     */
    fun normalize(url: String): String {
        val trimmed = url.trim()
        val uri =
            try {
                URI(trimmed)
            } catch (_: URISyntaxException) {
                return trimmed.trimEnd('/')
            }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return trimmed.trimEnd('/')
        val host = uri.host?.lowercase(Locale.ROOT) ?: return trimmed.trimEnd('/')
        val port =
            when {
                uri.port == -1 -> ""
                scheme == "http" && uri.port == 80 -> ""
                scheme == "https" && uri.port == 443 -> ""
                else -> ":${uri.port}"
            }
        val path = uri.rawPath.orEmpty().trimEnd('/')
        return "$scheme://$host$port$path"
    }

    /** The host of [url], to name a server when the user doesn't */
    fun host(url: String): String? =
        try {
            URI(url.trim()).host?.lowercase(Locale.ROOT)
        } catch (_: URISyntaxException) {
            null
        }
}
