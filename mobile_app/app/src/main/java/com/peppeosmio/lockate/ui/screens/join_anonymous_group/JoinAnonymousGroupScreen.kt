package com.peppeosmio.lockate.ui.screens.join_anonymous_group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.ui.composables.ErrorDialog
import com.peppeosmio.lockate.ui.composables.FormField
import com.peppeosmio.lockate.ui.composables.LabeledDivider
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun JoinAnonymousGroupScreen(
    connectionId: Long,
    code: String?,
    navigateBack: () -> Unit,
    navigateToHome: () -> Unit,
    navigateToAddServer: (NewServerInvite) -> Unit,
    viewModel: JoinAnonymousGroupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(connectionId, code) { viewModel.load(connectionId, code) }
    LaunchedEffect(true) {
        viewModel.snackbarEvents.collect { message ->
            if (message.errorInfo != null) {
                viewModel.showErrorDialog(message)
            } else {
                snackbarHostState.showSnackbar(message = message.text, withDismissAction = true)
            }
        }
    }
    LaunchedEffect(true) { viewModel.navigateHomeEvents.collect { navigateToHome() } }
    LaunchedEffect(true) { viewModel.addServerEvents.collect { navigateToAddServer(it) } }

    state.dialogError?.let { ErrorDialog(it, onDismiss = viewModel::hideErrorDialog) }

    // Google's scanner: its own screen, so the app needs no camera permission
    fun scan() {
        val options =
            GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(viewModel::onScanned) }
            .addOnFailureListener { viewModel.onScanFailed() }
    }

    val step = state.step
    if (step != null) {
        BackHandler { viewModel.cancelJoin() }
        Joining(step, onCancel = viewModel::cancelJoin)
        return
    }
    JoinForm(
        state = state,
        snackbarHostState = snackbarHostState,
        navigateBack = navigateBack,
        onScan = ::scan,
        viewModel = viewModel,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JoinForm(
    state: JoinAnonymousGroupState,
    snackbarHostState: SnackbarHostState,
    navigateBack: () -> Unit,
    onScan: () -> Unit,
    viewModel: JoinAnonymousGroupViewModel,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join a group") },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // the scaffold already pads for the navigation bar, which the keyboard covers
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Column(
                modifier =
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        append("Ask someone in the group to open ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Invite") }
                        append(
                            ". Scan the QR code on their phone, or type the number and three " +
                                "words they read you."
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onScan) {
                    Icon(
                        painterResource(R.drawable.baseline_qr_code_scanner_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Scan QR code")
                }
                LabeledDivider("or type it")
                FormField(
                    value = state.codeText,
                    onValueChange = viewModel::setCodeText,
                    label = "Invite code",
                    errorText = state.codeError,
                    monospace = true,
                )
                FormField(
                    value = state.memberNameText,
                    onValueChange = viewModel::setUserNameText,
                    label = "Your name in this group",
                    errorText = state.memberNameError,
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                onClick = viewModel::joinAnonymousGroup,
            ) {
                Text("Join")
            }
        }
    }
}

/** The exchange with the inviter's phone, one step after the other, with a way out */
@Composable
private fun Joining(step: JoinStep, onCancel: () -> Unit) {
    Surface(Modifier.fillMaxSize()) { JoiningContent(step, onCancel) }
}

@Composable
private fun JoiningContent(step: JoinStep, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        IconButton(modifier = Modifier.padding(4.dp), onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Cancel joining")
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.size(48.dp))
            CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 5.dp)
            Spacer(Modifier.size(32.dp))
            Text(
                "Waiting for the other phone",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "Keep Invite open on their phone. It takes a few seconds once both phones are " +
                    "online.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(40.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StepRow(
                    "Checking the code with their phone",
                    done = step == JoinStep.ReceivingKey,
                    current = step == JoinStep.CheckingCode,
                )
                StepRow(
                    "Receiving the group's key",
                    done = false,
                    current = step == JoinStep.ReceivingKey,
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun StepRow(text: String, done: Boolean, current: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                done ->
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                current -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 3.dp)
                else ->
                    Box(Modifier.size(16.dp)) {
                        CircularProgressIndicator(
                            progress = { 0f },
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
            color =
                if (current || done) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline,
        )
    }
}
