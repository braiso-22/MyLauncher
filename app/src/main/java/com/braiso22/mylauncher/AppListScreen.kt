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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.braiso22.mylauncher.domain.AppRepository
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
    repository: AppRepository,
    modifier: Modifier = Modifier,
) {
    var apps by remember { mutableStateOf<ImmutableList<AppInfo>>(persistentListOf()) }

    @Suppress("EffectKeys")
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            getInstalledApps(context)
        }
        apps = loaded
    }

    val favorites by repository.favorites.collectAsState()
    val blocked by repository.blocked.collectAsState()

    val debouncedQuery = rememberDebouncedValue(searchQuery)
    val filteredApps = remember(debouncedQuery, apps) {
        if (debouncedQuery.isBlank()) apps
        else apps.filter { it.label.contains(debouncedQuery, ignoreCase = true) }
    }

    // App whose blocked dialog is currently shown
    var blockedDialogApp by remember { mutableStateOf<AppInfo?>(null) }
    // App whose context menu is shown
    var contextMenuApp by remember { mutableStateOf<AppInfo?>(null) }

    blockedDialogApp?.let { app ->
        BlockedAppDialog(
            appName = app.label,
            onEnter = {
                blockedDialogApp = null
                launchApp(context, app)
            },
            onDismiss = { blockedDialogApp = null },
        )
    }

    contextMenuApp?.let { app ->
        AppContextMenu(
            app = app,
            isFavorite = app.packageName in favorites,
            isBlocked = app.packageName in blocked,
            onToggleFavorite = {
                repository.toggleFavorite(app.packageName)
                contextMenuApp = null
            },
            onToggleBlocked = {
                repository.toggleBlocked(app.packageName)
                contextMenuApp = null
            },
            onDismiss = { contextMenuApp = null },
        )
    }

    Column(modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onUpdateQuery,
            trailingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            placeholder = { Text("Search apps") },
        )
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
                items(filteredApps, key = { it.packageName }) { app ->
                    AppItem(
                        app = app,
                        isFavorite = app.packageName in favorites,
                        isBlocked = app.packageName in blocked,
                        onClick = {
                            if (app.packageName in blocked) {
                                blockedDialogApp = app
                            } else {
                                launchApp(context, app)
                            }
                        },
                        onLongClick = { contextMenuApp = app },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

private fun launchApp(context: Context, app: AppInfo) {
    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setClassName(app.packageName, app.activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(launchIntent)
}

@Composable
fun AppContextMenu(
    app: AppInfo,
    isFavorite: Boolean,
    isBlocked: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleBlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos") },
            onClick = onToggleFavorite,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        )
        DropdownMenuItem(
            text = { Text(if (isBlocked) "Desbloquear app" else "Bloquear app") },
            onClick = onToggleBlocked,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isBlocked) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        )
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
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
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
                repository = AppRepository(),
                modifier = Modifier.padding(padding)
            )
        }
    }
}