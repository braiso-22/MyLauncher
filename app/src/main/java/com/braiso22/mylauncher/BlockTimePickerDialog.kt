package com.braiso22.mylauncher

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme

@Suppress("ModifierRequired")
@Composable
fun BlockTimePickerDialog(
    appName: String,
    onConfirm: (minutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(1, 5, 10)
    var selectedMinutes by remember { mutableIntStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Bloquear $appName") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "¿Cuánto tiempo puedes usar esta app antes de recibir un aviso?")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    options.forEach { minutes ->
                        FilterChip(
                            selected = selectedMinutes == minutes,
                            onClick = { selectedMinutes = minutes },
                            label = { Text("$minutes min") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedMinutes) }) {
                Text("Bloquear")
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
fun BlockTimePickerDialogPreview() {
    MyLauncherTheme {
        BlockTimePickerDialog(
            appName = "Instagram",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

