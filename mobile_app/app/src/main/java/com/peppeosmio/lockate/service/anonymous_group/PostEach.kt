package com.peppeosmio.lockate.service.anonymous_group

import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Posts each item, one at a time and in order. One whose post failed while offline is posted again
 * as soon as the network returns, unless a newer item comes first and replaces it. With the network
 * there (the server is down) nothing is resent: the next item retries, so there is no burst.
 */
internal suspend fun <T> Flow<T>.postEach(
    post: suspend (T) -> Boolean,
    isNetworkAvailable: () -> Boolean,
    awaitNetworkAvailable: suspend () -> Unit,
) = coroutineScope {
    var resend: Job? = null
    collect { item ->
        // stops a resend waiting for the network, and lets one already posting finish, so its
        // circle events can't go out twice
        resend?.cancelAndJoin()
        if (!post(item) && !isNetworkAvailable()) {
            resend = launch {
                awaitNetworkAvailable()
                withContext(NonCancellable) { post(item) }
            }
        }
    }
}
