package com.peppeosmio.lockate.ui.screens.join_anonymous_group

import com.peppeosmio.lockate.utils.SnackbarErrorMessage

data class JoinAnonymousGroupState(
    val isJoining: Boolean = false,
    val codeText: String = "",
    val codeError: String? = null,
    val memberNameText: String = "",
    val memberNameError: String? = null,
    val dialogError: SnackbarErrorMessage? = null,
)
