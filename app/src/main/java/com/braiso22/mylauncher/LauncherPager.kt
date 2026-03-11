package com.braiso22.mylauncher

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
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
                context = LocalContext.current,
                searchQuery = query,
                onUpdateQuery = { query = it },
                repository = repository,
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
