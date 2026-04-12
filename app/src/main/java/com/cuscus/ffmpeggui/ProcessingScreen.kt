package com.cuscus.ffmpeggui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material.icons.rounded.ContentCopy

@Composable
fun ProcessingScreen(
    state: ConversionState,
    onCancel: () -> Unit,
    onNewConversion: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        when (state) {
            is ConversionState.Processing -> {
                ProcessingContent(
                    progress = state.progress,
                    logs = state.logs,
                    onCancel = onCancel,
                )
            }

            is ConversionState.Success -> {
                SuccessContent(
                    outputPath = state.outputPath,
                    onShare = {
                        val file = File(state.outputPath)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "*/*"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Condividi file"))
                    },
                    onNewConversion = onNewConversion,
                )
            }

            is ConversionState.Error -> {
                ErrorContent(
                    message = state.message,
                    logs = state.logs,
                    onRetry = onNewConversion,
                )
            }

            ConversionState.Idle -> {}
        }
    }
}

@Composable
private fun ProcessingContent(progress: Float, logs: List<String>, onCancel: () -> Unit) {
    var showLogs by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val infinitePulse = rememberInfiniteTransition(label = "prog_pulse")
    val glowAlpha by infinitePulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    Text(
        text = "Elaborazione in corso...",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Non chiudere l'app",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(48.dp))

    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { if (progress > 0f) progress else 0f },
            strokeWidth = 8.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(160.dp),
        )
        if (progress <= 0f) {
            CircularProgressIndicator(
                strokeWidth = 8.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                trackColor = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(160.dp),
            )
        }
        Text(
            text = if (progress > 0f) "${(progress * 100).toInt()}%" else "...",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    LinearProgressIndicator(
        progress = { progress.coerceAtLeast(0f) },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
    )

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedButton(
        onClick = { showLogs = !showLogs },
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
        ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = if (showLogs) "Nascondi log" else "Mostra log",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    TextButton(onClick = onCancel) {
        Text(
            text = "Annulla",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }

    AnimatedVisibility(visible = showLogs) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.height(16.dp))
            LogBox(logs = logs, listState = listState, modifier = Modifier.fillMaxWidth().height(200.dp))
        }
    }
}

@Composable
private fun SuccessContent(
    outputPath: String,
    onShare: () -> Unit,
    onNewConversion: () -> Unit,
) {
    Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(80.dp),
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Completato!",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = outputPath.substringAfterLast("/"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(48.dp))

    Button(
        onClick = onShare,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Icon(imageVector = Icons.Rounded.Share, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Condividi / Salva", style = MaterialTheme.typography.titleMedium)
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(
        onClick = onNewConversion,
        shape = RoundedCornerShape(12.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outlineVariant)
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(
            text = "Nuova conversione",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    logs: List<String>,
    onRetry: () -> Unit,
) {
    val listState = rememberLazyListState()
    var showLogs by remember { mutableStateOf(true) }

    Icon(
        imageVector = Icons.Rounded.Error,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(64.dp),
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Errore",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )

    Spacer(modifier = Modifier.height(24.dp))

    if (logs.isNotEmpty()) {
        LogBox(
            logs = logs,
            listState = listState,
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    Button(
        onClick = onRetry,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(text = "Torna all'inizio", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun LogBox(
    logs: List<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(logs) { line ->
                    Text(
                        text = line.trimEnd(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
        }

        if (logs.isNotEmpty()) {
            IconButton(
                onClick = {
                    val logText = logs.joinToString("\n") { it.trimEnd() }
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(logText))
                    android.widget.Toast.makeText(context, "Log copiati", android.widget.Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copia log",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}