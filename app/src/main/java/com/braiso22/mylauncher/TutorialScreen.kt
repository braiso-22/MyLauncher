package com.braiso22.mylauncher

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun TutorialScreen(
    onFinishTutorial: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val steps = listOf(
        TutorialStep(
            title = stringResource(R.string.welcome_title),
            description = stringResource(R.string.welcome_desc),
        ),
        TutorialStep(
            title = stringResource(R.string.usage_access_title),
            description = stringResource(R.string.usage_access_desc),
            actionText = stringResource(R.string.grant_permission),
            onAction = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        ),
        TutorialStep(
            title = stringResource(R.string.default_launcher_title),
            description = stringResource(R.string.default_launcher_desc),
            actionText = stringResource(R.string.open_settings),
            onAction = {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        ),
        TutorialStep(
            title = stringResource(R.string.all_set_title),
            description = stringResource(R.string.all_set_desc),
            actionText = stringResource(R.string.start),
            onAction = onFinishTutorial
        )
    )

    val pagerState = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pagerState.currentPage > 0) {
                    TextButton(onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }) {
                        Text(stringResource(R.string.previous))
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Row {
                    repeat(steps.size) { iteration ->
                        val color = if (pagerState.currentPage == iteration) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        }
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(8.dp)
                                .background(color, MaterialTheme.shapes.small)
                        )
                    }
                }

                if (pagerState.currentPage < steps.size - 1) {
                    TextButton(onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    }) {
                        Text(stringResource(R.string.next))
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            val step = steps[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = step.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = step.description,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                if (step.actionText != null) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = step.onAction ?: {}) {
                        Text(step.actionText)
                    }
                }
            }
        }
    }
}

data class TutorialStep(
    val title: String,
    val description: String,
    val actionText: String? = null,
    val onAction: (() -> Unit)? = null
)
