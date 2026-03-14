package com.braiso22.mylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme
import kotlinx.collections.immutable.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
)

fun getInstalledApps(context: Context): ImmutableList<AppInfo> {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val resolveInfos: List<ResolveInfo> =
        context.packageManager.queryIntentActivities(intent, 0)

    return resolveInfos.map { resolveInfo ->
        AppInfo(
            label = resolveInfo.loadLabel(context.packageManager).toString(),
            packageName = resolveInfo.activityInfo.packageName,
            activityName = resolveInfo.activityInfo.name,
        )
    }.sortedBy { it.label.lowercase() }.toImmutableList()
}

@Composable
fun rememberDebouncedValue(value: String, delayMillis: Long = 300L): String {
    var debouncedValue by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        delay(delayMillis)
        debouncedValue = value
    }
    return debouncedValue
}

@Composable
fun AppListScreen(
    searchQuery: String,
    onUpdateQuery: (String) -> Unit,
    context: Context,
    favorites: ImmutableSet<String>,
    blocked: ImmutableSet<String>,
    lastOpenedApp: AppInfo?,
    onToggleFavorite: (String) -> Unit,
    onBlockApp: (packageName: String, minutes: Int) -> Unit,
    onUnblockApp: (packageName: String) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onBlockedAppEntered: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    var apps by remember { mutableStateOf<ImmutableList<AppInfo>>(persistentListOf()) }

    @Suppress("EffectKeys")
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            getInstalledApps(context)
        }
        apps = loaded
    }

    val resolvedLastOpenedApp = remember(lastOpenedApp, apps) {
        lastOpenedApp ?: return@remember null
        apps.find { it.packageName == lastOpenedApp.packageName } ?: lastOpenedApp
    }

    val debouncedQuery = rememberDebouncedValue(searchQuery)

    // Whether we're showing the blocked apps list
    var showingBlocked by remember { mutableStateOf(false) }

    val filteredApps = remember(debouncedQuery, apps, blocked, showingBlocked) {
        if (showingBlocked) {
            // Show only blocked apps
            val list = apps.filter { it.packageName in blocked }
            if (debouncedQuery.isBlank()) list
            else list.filter { it.label.contains(debouncedQuery, ignoreCase = true) }
        } else {
            // Show only non-blocked apps
            val list = apps.filter { it.packageName !in blocked }
            if (debouncedQuery.isBlank()) list
            else list.filter { it.label.contains(debouncedQuery, ignoreCase = true) }
        }
    }

    // App whose blocked dialog is currently shown
    var blockedDialogApp by remember { mutableStateOf<AppInfo?>(null) }
    // App whose context menu is shown
    var contextMenuApp by remember { mutableStateOf<AppInfo?>(null) }
    // App for which the block time picker is shown
    var blockTimePickerApp by remember { mutableStateOf<AppInfo?>(null) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isActive) {
        if (isActive) focusRequester.requestFocus()
        else focusManager.clearFocus()
    }

    blockedDialogApp?.let { app ->
        BlockedAppDialog(
            appName = app.label,
            onEnter = {
                blockedDialogApp = null
                onBlockedAppEntered(app.packageName)
                onLaunchApp(app)
            },
            onDismiss = { blockedDialogApp = null },
        )
    }

    blockTimePickerApp?.let { app ->
        BlockTimePickerDialog(
            appName = app.label,
            onConfirm = { minutes ->
                onBlockApp(app.packageName, minutes)
                blockTimePickerApp = null
            },
            onDismiss = { blockTimePickerApp = null },
        )
    }

    contextMenuApp?.let { app ->
        AppContextMenu(
            isFavorite = app.packageName in favorites,
            isBlocked = app.packageName in blocked,
            onToggleFavorite = {
                onToggleFavorite(app.packageName)
                contextMenuApp = null
            },
            onToggleBlock = {
                contextMenuApp = null
                if (app.packageName in blocked) {
                    onUnblockApp(app.packageName)
                } else {
                    blockTimePickerApp = app
                }
            },
            onDismiss = { contextMenuApp = null },
        )
    }

    Column(modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onUpdateQuery,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onUpdateQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                }else {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .focusRequester(focusRequester),
            placeholder = { Text("Search apps") },
        )

        // Toggle button for blocked apps
        if (blocked.isNotEmpty()) {
            FilterChip(
                selected = showingBlocked,
                onClick = { showingBlocked = !showingBlocked },
                label = {
                    Text(
                        if (showingBlocked) "Ver todas las apps"
                        else "Apps bloqueadas (${blocked.size})"
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (showingBlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (apps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Only show last opened app when NOT in blocked view and it's not blocked
                if (!showingBlocked) {
                    resolvedLastOpenedApp?.let { app ->
                        if (app.packageName !in blocked) {
                            item(key = "last_opened", contentType = "contentType1") {
                                LastOpenedAppItem(
                                    app = app,
                                    isBlocked = false,
                                    onClick = { onLaunchApp(app) },
                                    onLongClick = { contextMenuApp = app },
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
                items(
                    items = filteredApps,
                    key = { it.packageName },
                    contentType = { _ -> "contentType2" }) { app ->
                    if (showingBlocked) {
                        // In blocked view: tap opens blocked dialog, long press for context menu
                        AppItem(
                            app = app,
                            isFavorite = app.packageName in favorites,
                            isBlocked = true,
                            onClick = { blockedDialogApp = app },
                            onLongClick = { contextMenuApp = app },
                        )
                    } else {
                        // Normal view: only non-blocked apps
                        AppItem(
                            app = app,
                            isFavorite = app.packageName in favorites,
                            isBlocked = false,
                            onClick = { onLaunchApp(app) },
                            onLongClick = { contextMenuApp = app },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppContextMenu(
    isFavorite: Boolean,
    isBlocked: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleBlock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opciones de la app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    onClick = onToggleBlock,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isBlocked) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBlocked) "Desbloquear app" else "Bloquear app",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LastOpenedAppItem(
    app: AppInfo,
    isBlocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Última app abierta",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = app.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isBlocked)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    app: AppInfo,
    isFavorite: Boolean,
    isBlocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = app.label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = if (isBlocked)
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorito",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Suppress("ModifierRequired")
@PreviewLightDark
@Composable
fun AppListScreenPreview() {
    MyLauncherTheme {
        Scaffold { padding ->
            AppListScreen(
                searchQuery = "",
                onUpdateQuery = {},
                context = LocalContext.current,
                favorites = persistentSetOf<String>(),
                blocked = persistentSetOf<String>(),
                lastOpenedApp = null,
                onToggleFavorite = {},
                onBlockApp = { _, _ -> },
                onUnblockApp = {},
                onLaunchApp = {},
                onBlockedAppEntered = {},
                modifier = Modifier.padding(padding),
            )
        }
    }
}