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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.braiso22.mylauncher.domain.AppRepository
import com.braiso22.mylauncher.ui.theme.MyLauncherTheme


@Composable
fun LauncherPager(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { AppRepository.getInstance(context) }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 },
    )

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (pagerState.currentPage != 0) {
            pagerState.requestScrollToPage(0)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            0 -> Greeting(repository = repository)
            1 -> AppListScreen(
                repository = repository,
                context = context,
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
