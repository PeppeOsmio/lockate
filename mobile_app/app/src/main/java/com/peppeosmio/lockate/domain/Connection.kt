package com.peppeosmio.lockate.domain

data class Connection(
    val id: Long?, val url: String, val apiKey: String?, val username: String?, val authToken: String?
) {
    fun getWebSocketUrl(): String {
        if(url.startsWith("https")) {
            return url.replace("https", "wss")
        }
        if(url.startsWith("http")) {
            return url.replace("http", "ws")
        }
        return "ws://$url"
    }
}
