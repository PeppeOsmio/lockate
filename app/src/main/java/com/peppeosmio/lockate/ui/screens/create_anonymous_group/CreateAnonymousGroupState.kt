package com.peppeosmio.lockate.ui.screens.create_anonymous_group

import com.peppeosmio.lockate.utils.ErrorInfo

data class CreateAnonymousGroupState(
    val showLoadingOverlay: Boolean = false,
    val groupNameText: String = "",
    val groupNameError: String? = null,
    val memberPasswordText: String = "",
    val memberPasswordError: String? = null,
    val adminPasswordText: String = "",
    val adminPasswordError: String? = null,
    val userNameText: String = "",
    val userNameError: String? = null,
    val errorInfo: ErrorInfo? = null
)
