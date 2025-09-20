package com.peppeosmio.lockate.ui.screens.join_anonymous_group

import com.peppeosmio.lockate.utils.ErrorDialogInfo

data class JoinAnonymousGroupState(
    val showLoadingOverlay: Boolean = false,
    val idText: String = "",
    val idError: String? = null,
    val memberPasswordText: String = "",
    val memberPasswordError: String? = null,
    val memberNameText: String= "",
    val memberNameError: String? = null,
    val dialogErrorDialogInfo: ErrorDialogInfo? = null,
)
