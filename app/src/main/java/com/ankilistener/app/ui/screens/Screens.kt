package com.ankilistener.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankilistener.app.ui.viewmodel.ReviewState
import com.ankilistener.app.ui.viewmodel.ReviewViewModel
import com.ankilistener.app.util.HtmlUtils.toAnnotatedString
import com.ankilistener.app.util.HtmlUtils.parseHtml
import kotlin.math.abs

@Composable
fun PermissionScreen(isInstalled: Boolean, onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (!isInstalled) "未安�?AnkiDroid" else "需要授�?,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (!isInstalled) "请先安装 AnkiDroid 并开�?API 权限�? else "请在弹出框中点击允许，以便读取卡片�?,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (isInstalled) {
            Button(onClick = onGrantClick) {
                Text("授予权限")
            }
        }
    }
}

@Composable
fun DeckSelectionScreen(viewModel: ReviewViewModel, onDeckClick: (Long) -> Unit) {
    val decks by viewModel.decks
    
    LaunchedEffect(Unit) {
        viewModel.loadDecks()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "选择牌组",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn {
            items(decks) { deck ->
                ListItem(
                    headlineContent = { Text(deck.name) },
                    modifier = Modifier.clickable { onDeckClick(deck.id) }
                )
                Divider()
            }
        }
    }
}

@Composable
fun ReviewScreen(viewModel: ReviewViewModel, onFinished: () -> Unit) {
    val state by viewModel.reviewState
    val card = viewModel.currentCard

    if (state == ReviewState.FINISHED) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("复习完成", style = MaterialTheme.typography.headlineLarge)
        }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            onFinished()
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { viewModel.onSingleTap() },
                    onDoubleTap = { viewModel.onDoubleTap() }
                )
            }
            .pointerInput(Unit) {
                var dragAmount = 0f
                var dragDirection = "" // "H" or "V"
                
                detectDragGestures(
                    onDragStart = { dragAmount = 0f; dragDirection = "" },
                    onDrag = { change, amount ->
                        change.consume()
                        if (dragDirection == "") {
                            dragDirection = if (abs(amount.x) > abs(amount.y)) "H" else "V"
                        }
                        dragAmount += if (dragDirection == "H") amount.x else amount.y
                    },
                    onDragEnd = {
                        if (abs(dragAmount) > 100) {
                            if (dragDirection == "H") {
                                if (dragAmount > 0) viewModel.onSwipeRight() else viewModel.onSwipeLeft()
                            } else {
                                if (dragAmount > 0) viewModel.onSwipeDown()
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            card?.let {
                if (state == ReviewState.FRONT) {
                    Text(
                        text = parseHtml(it.front).toAnnotatedString(),
                        fontSize = 32.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 40.sp
                    )
                } else if (state == ReviewState.BACK) {
                    Text(
                        text = parseHtml(it.front).toAnnotatedString(),
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(modifier = Modifier.fillMaxWidth(0.8f))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = parseHtml(it.back).toAnnotatedString(),
                        fontSize = 32.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
