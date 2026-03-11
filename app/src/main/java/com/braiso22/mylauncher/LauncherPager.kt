package com.braiso22.mylauncher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.braiso22.mylauncher.domain.AppRepository
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme

@Composable
fun LauncherPager(modifier: Modifier = Modifier) {
    val repository = remember { AppRepository() }

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
            0 -> Greeting(repository = repository)
            1 -> AppListScreen(
                context = LocalContext.current,
                searchQuery = query,
                onUpdateQuery = { query = it },
                repository = repository,
                modifier = Modifier.padding(horizontal = 16.dp),
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
