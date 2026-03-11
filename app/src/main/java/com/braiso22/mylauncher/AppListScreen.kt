package com.braiso22.mylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val list = resolveInfos.map { resolveInfo ->
        AppInfo(
            label = resolveInfo.loadLabel(context.packageManager).toString(),
            packageName = resolveInfo.activityInfo.packageName,
            activityName = resolveInfo.activityInfo.name,
        )
    }.sortedBy { it.label.lowercase() }.toImmutableList()

    return list
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

    val debouncedQuery = rememberDebouncedValue(searchQuery)
    val filteredApps = remember(debouncedQuery, apps) {
        if (debouncedQuery.isBlank()) apps
        else apps.filter { it.label.contains(debouncedQuery, ignoreCase = true) }
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
                        onClick = {
                            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_LAUNCHER)
                                setClassName(app.packageName, app.activityName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(launchIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    )
                }
            }
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
                modifier = Modifier.padding(padding)
            )
        }
    }
}


@Composable
fun AppItem(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = app.label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}


@Composable
fun LauncherPager(modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 },
    )

    var query by remember { mutableStateOf("") }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            0 -> Greeting()
            1 -> AppListScreen(
                context = LocalContext.current,
                searchQuery = query,
                onUpdateQuery = { query = it },
                modifier = Modifier.padding(
                    horizontal = 16.dp
                )
            )
        }
    }
}


@Composable
fun Greeting(modifier: Modifier = Modifier) {
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    var currentDate by remember { mutableStateOf(getCurrentDate()) }

    @Suppress("EffectKeys")
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTime()
            currentDate = getCurrentDate()
            delay(1000L)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = currentTime,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = currentDate,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }
    }
}

fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

fun getCurrentDate(): String {
    return SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
}

@Suppress("ModifierRequired")
@PreviewLightDark
@Composable
fun GreetingPreview() {
    MyLauncherTheme {
        Scaffold { padding ->
            Greeting(modifier = Modifier.padding(padding))
        }
    }
}