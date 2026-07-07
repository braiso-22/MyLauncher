package com.braiso22.mylauncher

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.braiso22.mylauncher.data.TimeBankManager
import com.braiso22.mylauncher.domain.AppRepository
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Composable
fun Greeting(
    repository: AppRepository,
    onShowTutorial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    var currentDate by remember { mutableStateOf(getCurrentDate()) }

    @Suppress("EffectKeys")
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTime()
            currentDate = getCurrentDate()
            delay(1L.seconds)
        }
    }

    var allApps by remember { mutableStateOf<ImmutableList<AppInfo>>(persistentListOf()) }
    @Suppress("EffectKeys")
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.IO) { getInstalledApps(context) }
    }

    val favorites by repository.favorites.collectAsStateWithLifecycle()
    val favoriteApps = remember(favorites, allApps) {
        allApps.filter { it.packageName in favorites }.toImmutableList()
    }

    val blocked by repository.blocked.collectAsStateWithLifecycle()
    val blockTimes by repository.blockTimes.collectAsStateWithLifecycle()

    // Time bank (read-only display for now — reconciles on open and periodically)
    val timeBankManager = remember { TimeBankManager.getInstance(context) }
    val timeBankSnapshot by timeBankManager.snapshot.collectAsStateWithLifecycle()
    @Suppress("EffectKeys")
    LaunchedEffect(Unit) {
        while (true) {
            timeBankManager.refresh()
            delay(15L.seconds)
        }
    }

    // App cuyo dialog de bloqueo se muestra
    var blockedDialogApp by remember { mutableStateOf<AppInfo?>(null) }

    blockedDialogApp?.let { app ->
        BlockedAppDialog(
            appName = app.label,
            onEnter = {
                blockedDialogApp = null
                repository.markAppUnlocked(app.packageName, blockTimes[app.packageName] ?: 5)
                launchFavoriteApp(context, app)
            },
            onDismiss = { blockedDialogApp = null },
        )
    }

    val balanceText = stringResource(
        R.string.time_bank_balance,
        formatBankDuration(timeBankSnapshot.balance),
    )
    val earnText = timeBankSnapshot.earnableNow
        .takeIf { !it.isZero && !it.isNegative }
        ?.let { stringResource(R.string.time_bank_earn_now, formatBankDuration(it)) }

    GreetingContent(
        favoriteApps = favoriteApps,
        currentTime = currentTime,
        currentDate = currentDate,
        balanceText = balanceText,
        earnText = earnText,
        onAppClick = { app ->
            if (app.packageName in blocked) {
                if (repository.isAppUnlocked(app.packageName)) {
                    // App still has a valid unlock — refresh expiry and launch directly
                    repository.markAppUnlocked(app.packageName, blockTimes[app.packageName] ?: 5)
                    launchFavoriteApp(context, app)
                } else {
                    blockedDialogApp = app
                }
            } else {
                launchFavoriteApp(context, app)
            }
        },
        onSettingsClick = onShowTutorial,
        modifier = modifier,
    )
}

@Composable
fun GreetingContent(
    favoriteApps: ImmutableList<AppInfo>,
    currentTime: String,
    currentDate: String,
    balanceText: String,
    earnText: String?,
    onAppClick: (AppInfo) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Settings button in the top right
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Reloj en la parte superior
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = currentTime,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = currentDate,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                )
                Text(
                    text = balanceText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (earnText != null) {
                    Text(
                        text = earnText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    )
                }
            }

            // Favoritos ocupando el resto del espacio
            if (favoriteApps.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(favoriteApps, key = { it.packageName }) { app ->
                        FavoriteAppItem(
                            app = app,
                            onClick = { onAppClick(app) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteAppItem(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = app.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

private fun launchFavoriteApp(context: Context, app: AppInfo) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setClassName(app.packageName, app.activityName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/** Formats a bank duration as e.g. "1h 5m", "12m" or "0m" (rounded down to whole minutes). */
fun formatBankDuration(duration: Duration): String {
    val totalMinutes = duration.coerceAtLeast(Duration.ZERO).toMinutes()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

fun getCurrentTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

fun getCurrentDate(): String =
    SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())

@PreviewLightDark
@Composable
private fun GreetingContentPreview() {
    val sampleApps = listOf(
        AppInfo(label = "WhatsApp", packageName = "com.whatsapp", activityName = "com.whatsapp.Main"),
        AppInfo(label = "Chrome", packageName = "com.android.chrome", activityName = "com.google.android.apps.chrome.Main"),
        AppInfo(label = "Spotify", packageName = "com.spotify.music", activityName = "com.spotify.MainActivity"),
        AppInfo(label = "Maps", packageName = "com.google.android.apps.maps", activityName = "com.google.android.maps.MapsActivity"),
    ).toImmutableList()

    MyLauncherTheme {
        Scaffold { paddingValues ->
            GreetingContent(
                favoriteApps = sampleApps,
                currentTime = "14:30",
                currentDate = "viernes, 14 marzo",
                balanceText = "Saldo: 12m",
                earnText = "+7m si desbloqueas ahora",
                onAppClick = {},
                onSettingsClick = {},
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
