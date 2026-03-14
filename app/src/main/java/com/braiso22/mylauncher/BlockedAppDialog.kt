package com.braiso22.mylauncher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme
import kotlinx.coroutines.delay

private const val BLOCK_DELAY_SECONDS = 10
private const val CAPTCHA_LENGTH = 6

private fun generateRandomCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789"
    return (1..CAPTCHA_LENGTH).map { chars.random() }.joinToString("")
}

@Suppress("ModifierRequired")
@Composable
fun BlockedAppDialog(
    appName: String,
    onEnter: () -> Unit,
    onDismiss: () -> Unit,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var ready by remember { mutableStateOf(false) }
    val captchaCode by remember { mutableStateOf(generateRandomCode()) }
    var userInput by remember { mutableStateOf("") }

    val inputMatches = userInput == captchaCode

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
                if (!ready) {
                    Text(text = "Esta app está bloqueada. Espera para poder abrirla.")
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(text = "Escribe el siguiente código para continuar:")
                    Text(
                        text = captchaCode,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        singleLine = true,
                        label = { Text("Código") },
                        isError = userInput.isNotEmpty() && !inputMatches,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onEnter,
                enabled = ready && inputMatches,
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



