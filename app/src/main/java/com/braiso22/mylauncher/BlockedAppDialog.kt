package com.braiso22.mylauncher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme
import kotlinx.coroutines.delay

private const val BLOCK_DELAY_SECONDS = 10

@Suppress("ModifierRequired")
@Composable
fun BlockedAppDialog(
    appName: String,
    onEnter: () -> Unit,
    onDismiss: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var ready by remember { mutableStateOf(false) }

    @Suppress("EffectKeys")
    LaunchedEffect(Unit) {
        val steps = BLOCK_DELAY_SECONDS * 10
        val stepDelay = 1000L / 10
        repeat(steps) {
            delay(stepDelay)
            progress = (it + 1) / steps.toFloat()
        }
        ready = true
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 80),
        label = "blockProgress"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = appName) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Esta app está bloqueada. Espera para poder abrirla.")
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onEnter,
                enabled = ready,
            ) {
                Text("Entrar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Suppress("ModifierRequired")
@PreviewLightDark
@Composable
fun BlockedAppDialogPreview() {
    MyLauncherTheme {
        BlockedAppDialog(
            appName = "YouTube",
            onEnter = {},
            onDismiss = {},
        )
    }
}



