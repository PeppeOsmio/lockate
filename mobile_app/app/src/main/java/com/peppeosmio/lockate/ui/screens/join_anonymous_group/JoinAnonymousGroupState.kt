package com.peppeosmio.lockate.ui.screens.join_anonymous_group

import com.peppeosmio.lockate.utils.SnackbarErrorMessage

/** Where the exchange with the inviter's phone is, while joining */
enum class JoinStep {
    CheckingCode,
    ReceivingKey,
}

data class JoinAnonymousGroupState(
    // null while filling the form
    val step: JoinStep? = null,
    val codeText: String = "",
    val codeError: String? = null,
    val memberNameText: String = "",
    val memberNameError: String? = null,
    val dialogError: SnackbarErrorMessage? = null,
)
