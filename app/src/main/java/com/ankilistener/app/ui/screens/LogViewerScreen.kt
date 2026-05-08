package com.ankilistener.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankilistener.app.util.AppLogger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    var entries by remember { mutableStateOf(AppLogger.getEntries()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Listen for new log entries
    DisposableEffect(Unit) {
        val listener: () -> Unit = {
            entries = AppLogger.getEntries()
        }
        AppLogger.addListener(listener)
        onDispose {
            AppLogger.removeListener(listener)
        }
    }

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日志 (${entries.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        AppLogger.clear()
                        entries = emptyList()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "清除日志")
                    }
                }
            )
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无日志", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
                state = listState
            ) {
                items(entries) { entry ->
                    val color = when (entry.level) {
                        AppLogger.Level.ERROR -> Color(0xFFEF5350)
                        AppLogger.Level.WARN -> Color(0xFFFF9800)
                        AppLogger.Level.INFO -> Color(0xFF66BB6A)
                        AppLogger.Level.DEBUG -> Color(0xFF90A4AE)
                    }
                    Text(
                        text = entry.formatted(),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = color,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}
