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
            title = "Bienvenido a MyLauncher",
            description = "Un lanzador minimalista diseñado para ayudarte a concentrarte.",
        ),
        TutorialStep(
            title = "Permiso de Acceso a Datos de Uso",
            description = "Para que el bloqueo de aplicaciones funcione, necesitamos saber qué aplicación estás usando. Por favor, concede este permiso en los ajustes.",
            actionText = "Conceder Permiso",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        ),
        TutorialStep(
            title = "Establecer como Predeterminado",
            description = "Para disfrutar de la experiencia completa, establece MyLauncher como tu pantalla de inicio predeterminada en los ajustes del sistema.",
            actionText = "Abrir Ajustes",
            onAction = {
                context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            }
        ),
        TutorialStep(
            title = "¡Todo listo!",
            description = "Ahora puedes empezar a usar MyLauncher y recuperar tu tiempo.",
            actionText = "Comenzar",
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
                        Text("Anterior")
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
                        Text("Siguiente")
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
