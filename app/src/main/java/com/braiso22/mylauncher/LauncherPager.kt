package com.braiso22.mylauncher

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.braiso22.mylauncher.domain.AppRepository
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme
import kotlinx.collections.immutable.toImmutableSet

@Composable
fun LauncherPager(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { AppRepository.getInstance(context) }

    val favorites by repository.favorites.collectAsStateWithLifecycle()
    val blocked by repository.blocked.collectAsStateWithLifecycle()
    val lastOpenedPackage by repository.lastOpenedPackage.collectAsStateWithLifecycle()

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        allApps = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            getInstalledApps(context)
        }
    }
    val lastOpenedApp = remember(lastOpenedPackage, allApps) {
        lastOpenedPackage?.let { pkg -> allApps.find { it.packageName == pkg } }
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 },
    )

    var query by remember { mutableStateOf("") }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 1) query = ""
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            0 -> Greeting(repository = repository)
            1 -> AppListScreen(
                context = context,
                searchQuery = query,
                onUpdateQuery = { query = it },
                favorites = favorites.toImmutableSet(),
                blocked = blocked.toImmutableSet(),
                lastOpenedApp = lastOpenedApp,
                onToggleFavorite = { repository.toggleFavorite(it) },
                onBlockApp = { pkg, minutes -> repository.blockApp(pkg, minutes) },
                onUnblockApp = { pkg -> repository.unblockApp(pkg) },
                onLaunchApp = { app ->
                    repository.setLastOpened(app.packageName)
                    val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        setClassName(app.packageName, app.activityName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(launchIntent)
                },
                onBlockedAppEntered = { pkg ->
                    repository.markBlockedAppOpened(pkg)
                },
                isActive = pagerState.currentPage == 1,
                modifier = Modifier,
            )
        }
    }
}

@Suppress("ModifierRequired")
@PreviewLightDark
@Composable
fun LauncherPagerPreview() {
    MyLauncherTheme {
        Scaffold { padding ->
            LauncherPager(modifier = Modifier.padding(padding))
        }
    }
}
