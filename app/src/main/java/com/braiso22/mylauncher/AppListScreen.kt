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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.braiso22.mylauncher.domain.AppRepository
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

// ── Wrapper ──────────────────────────────────────────────────────────────────

@Composable
fun AppListScreen(
    repository: AppRepository,
    context: Context,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var allApps by remember { mutableStateOf<ImmutableList<AppInfo>>(persistentListOf()) }

    @Suppress("EffectKeys")
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { getInstalledApps(context) }
    }

    val favorites by repository.favorites.collectAsStateWithLifecycle()
    val blocked by repository.blocked.collectAsStateWithLifecycle()
    val blockTimes by repository.blockTimes.collectAsStateWithLifecycle()
    val lastOpenedPackage by repository.lastOpenedPackage.collectAsStateWithLifecycle()

    val lastOpenedApp = remember(lastOpenedPackage, allApps) {
        lastOpenedPackage?.let { pkg -> allApps.find { it.packageName == pkg } }
    }

    var query by remember { mutableStateOf("") }
    var showingBlocked by remember { mutableStateOf(false) }

    LaunchedEffect(isActive) {
        if (!isActive) {
            query = ""
            showingBlocked = false
        }
    }

    val debouncedQuery = rememberDebouncedValue(query)

    val filteredApps = remember(debouncedQuery, allApps, blocked, showingBlocked) {
        val base = if (showingBlocked) {
            allApps.filter { it.packageName in blocked }
        } else {
            allApps.filter { it.packageName !in blocked }
        }
        if (debouncedQuery.isBlank()) base.toImmutableList()
        else base.filter { it.label.contains(debouncedQuery, ignoreCase = true) }.toImmutableList()
    }

    AppListScreenContent(
        searchQuery = query,
        onUpdateQuery = { query = it },
        showingBlocked = showingBlocked,
        onToggleShowBlocked = { showingBlocked = !showingBlocked },
        filteredApps = filteredApps,
        favorites = favorites.toImmutableSet(),
        blocked = blocked.toImmutableSet(),
        blockTimes = blockTimes.toImmutableMap(),
        lastOpenedApp = lastOpenedApp,
        onToggleFavorite = { repository.toggleFavorite(it) },
        onBlockApp = { pkg, minutes -> repository.blockApp(pkg, minutes) },
        onLaunchApp = { app ->
            repository.setLastOpened(app.packageName)
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(app.packageName, app.activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent)
        },
        onBlockedAppEntered = { pkg -> repository.markBlockedAppOpened(pkg) },
        isActive = isActive,
        modifier = modifier,
    )
}

// ── Contenido puro ───────────────────────────────────────────────────────────

@Composable
fun AppListScreenContent(
    searchQuery: String,
    onUpdateQuery: (String) -> Unit,
    showingBlocked: Boolean,
    onToggleShowBlocked: () -> Unit,
    filteredApps: ImmutableList<AppInfo>,
    favorites: ImmutableSet<String>,
    blocked: ImmutableSet<String>,
    blockTimes: ImmutableMap<String, Int>,
    lastOpenedApp: AppInfo?,
    onToggleFavorite: (String) -> Unit,
    onBlockApp: (packageName: String, minutes: Int) -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onBlockedAppEntered: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
) {
    // App whose blocked dialog is currently shown
    var blockedDialogApp by remember { mutableStateOf<AppInfo?>(null) }
    // App whose context menu is shown
    var contextMenuApp by remember { mutableStateOf<AppInfo?>(null) }
    // App for which the block time picker is shown
    var blockTimePickerApp by remember { mutableStateOf<AppInfo?>(null) }
    // Whether to show the about project dialog
    var showAboutDialog by remember { mutableStateOf(false) }

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
            initialMinutes = blockTimes[app.packageName] ?: 5,
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
                blockTimePickerApp = app
            },
            onDismiss = { contextMenuApp = null },
        )
    }

    if (showAboutDialog) {
        AboutProjectDialog(onDismiss = { showAboutDialog = false })
    }

    Column(modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onUpdateQuery,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onUpdateQuery("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                } else {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .focusRequester(focusRequester),
            placeholder = { Text(stringResource(R.string.search_apps)) },
        )

        // Toggle button for blocked apps
        if (blocked.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilterChip(
                    selected = showingBlocked,
                    onClick = onToggleShowBlocked,
                    label = {
                        Text(
                            if (showingBlocked) stringResource(R.string.view_all_apps)
                            else stringResource(R.string.blocked_apps, blocked.size)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (showingBlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                IconButton(onClick = { showAboutDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.info_about_project),
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Only show last opened app when NOT in blocked view and it's not blocked
            if (!showingBlocked) {
                lastOpenedApp?.let { app ->
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
                contentType = { _ -> "contentType2" },
            ) { app ->
                if (showingBlocked) {
                    AppItem(
                        app = app,
                        isFavorite = app.packageName in favorites,
                        isBlocked = true,
                        onClick = { blockedDialogApp = app },
                        onLongClick = { contextMenuApp = app },
                    )
                } else {
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

// ── Componentes reutilizables ─────────────────────────────────────────────────

@Composable
fun AboutProjectDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val paypalUrl = "https://www.paypal.com/paypalme/braiso22"
    val githubUrl = "https://github.com/braiso-22"
    val email = "braisfv22@gmail.com"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_project_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.about_project_message))
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {

                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, paypalUrl.toUri())
                            context.startActivity(intent)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.paypal),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                        )
                    }

                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, githubUrl.toUri())
                            context.startActivity(intent)
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.github),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                        )
                    }


                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = "mailto:$email".toUri()
                            }
                            try {
                                context.startActivity(
                                    Intent.createChooser(
                                        intent,
                                        "Send email"
                                    )
                                )
                            } catch (e: Exception) {
                                // Handle case where no email app is installed
                            }
                        },
                    ) {
                        Text(
                            text = email,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        modifier = modifier
    )
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
        title = { Text(stringResource(R.string.app_options)) },
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
                        text = if (isFavorite) stringResource(R.string.remove_from_favorites) else stringResource(
                            R.string.add_to_favorites
                        ),
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
                        tint = if (isBlocked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBlocked) stringResource(R.string.change_block_time) else stringResource(
                            R.string.block_app
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
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
            text = stringResource(R.string.last_opened_app),
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
                contentDescription = stringResource(R.string.favorite),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Suppress("ModifierRequired")
@PreviewLightDark
@Composable
fun AppListScreenContentPreview() {
    val sampleFavorites = persistentSetOf("com.android.chrome")
    val sampleBlocked = persistentSetOf("com.instagram.android")
    val sampleApps = persistentListOf(
        AppInfo("Chrome", "com.android.chrome", "com.google.android.apps.chrome.Main"),
        AppInfo("Spotify", "com.spotify.music", "com.spotify.MainActivity"),
        AppInfo(
            "Instagram (bloqueada)",
            "com.instagram.android",
            "com.instagram.android.MainActivity"
        ),
        AppInfo("WhatsApp", "com.whatsapp", "com.whatsapp.Main"),
    )
    val lastOpened = AppInfo(
        label = "Chrome",
        packageName = "com.android.chrome",
        activityName = "com.google.android.apps.chrome.Main",
    )
    MyLauncherTheme {
        Scaffold { padding ->
            AppListScreenContent(
                searchQuery = "",
                onUpdateQuery = {},
                showingBlocked = false,
                onToggleShowBlocked = {},
                filteredApps = sampleApps,
                favorites = sampleFavorites,
                blocked = sampleBlocked,
                blockTimes = persistentMapOf(),
                lastOpenedApp = lastOpened,
                onToggleFavorite = {},
                onBlockApp = { _, _ -> },
                onLaunchApp = {},
                onBlockedAppEntered = {},
                modifier = Modifier.padding(padding),
            )
        }
    }
}
