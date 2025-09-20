package com.peppeosmio.lockate.ui.composables

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.peppeosmio.lockate.service.PermissionsService

/**
 * Request
 */
data class PermissionsRequest(
    val permissions: List<String>,
    val dialogTitle: String,
    val dialogText: String,
    val dialogGrantButton: String,
    val dialogDenyButton: String,
)

@Composable
fun PermissionsRequester(
    permissionRequests: List<PermissionsRequest>, onPermissionsRequested: (() -> Unit)? = null,
    checkPermissionGranted: (permission: String) -> Boolean,
    requestPermissions: (permissions: List<String>) -> Unit
) {
    val permissionsQueue = remember {
        mutableStateListOf(
            *permissionRequests.filter { permissionRequest ->
                permissionRequest.permissions.all {
                    !checkPermissionGranted(it)
                }
            }.toTypedArray()
        )
    }

    LaunchedEffect(key1 = permissionsQueue.size) {
        if (permissionsQueue.isNotEmpty()) {
            return@LaunchedEffect
        }
        if (onPermissionsRequested != null) {
            onPermissionsRequested()
        }
    }

    if (permissionsQueue.isEmpty()) {
        return
    }

    AlertDialog(title = { Text(permissionsQueue[0].dialogTitle) }, text = {
        Text(
            permissionsQueue[0].dialogText
        )
    }, confirmButton = {
        TextButton(onClick = {
            requestPermissions(permissionsQueue[0].permissions)
            permissionsQueue.removeAt(0)
        }) { Text(permissionsQueue[0].dialogGrantButton) }
    }, dismissButton = {
        TextButton(
            onClick = { permissionsQueue.removeAt(0) }) {
            Text(permissionsQueue[0].dialogDenyButton)
        }
    }, onDismissRequest = {
        permissionsQueue.removeAt(0)
    })
}
