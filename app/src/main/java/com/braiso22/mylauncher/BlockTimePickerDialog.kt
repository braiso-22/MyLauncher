package com.braiso22.mylauncher

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme

@Suppress("ModifierRequired")
@Composable
fun BlockTimePickerDialog(
    appName: String,
    initialMinutes: Int = 5,
    onConfirm: (minutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(1, 5, 10)
    var selectedMinutes by remember { mutableIntStateOf(initialMinutes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.block_app_title, appName)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.block_time_question))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    options.forEach { minutes ->
                        FilterChip(
                            selected = selectedMinutes == minutes,
                            onClick = { selectedMinutes = minutes },
                            label = { Text(stringResource(R.string.minutes_label, minutes)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedMinutes) }) {
                Text(stringResource(R.string.block))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
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
