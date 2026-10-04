package com.peppeosmio.lockate.ui.screens.create_anonymous_group

import com.peppeosmio.lockate.utils.SnackbarErrorMessage

data class CreateAnonymousGroupState(
    val showLoadingOverlay: Boolean = false,
    val groupNameText: String = "",
    val groupNameError: String? = null,
    val memberPasswordText: String = "",
    val memberPasswordError: String? = null,
    val userNameText: String = "",
    val userNameError: String? = null,
    val dialogError: SnackbarErrorMessage? = null
)
