package com.ankilistener.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ankilistener.app.data.ConceptReviewState
import com.ankilistener.app.ui.viewmodel.AiAnswerPhase
import com.ankilistener.app.ui.viewmodel.AiAnswerUiState
import com.ankilistener.app.ui.viewmodel.ReviewState
import com.ankilistener.app.ui.viewmodel.ReviewViewModel
import com.ankilistener.app.util.HtmlUtils
import com.ankilistener.app.util.HtmlUtils.toAnnotatedString
import com.ankilistener.app.util.HtmlUtils.parseHtml
import com.ankilistener.app.util.HtmlUtils.parseConceptLinks

@Composable
fun PermissionScreen(isInstalled: Boolean, onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (!isInstalled) "未安装 AnkiDroid" else "需要授权",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (!isInstalled) "请先安装 AnkiDroid 并开启 API 权限。" else "请在弹出框中点击允许，以便读取卡片。",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckSelectionScreen(
    viewModel: ReviewViewModel, 
    onDeckClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onLogClick: () -> Unit = {}
) {
    val decks by viewModel.decks
    
    LaunchedEffect(Unit) {
        viewModel.loadDecks()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择牌组") },
                actions = {
                    IconButton(onClick = onLogClick) {
                        Text("📋", fontSize = 20.sp)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
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
    val gestureFeedback by viewModel.gestureFeedback
    val fontScale by viewModel.fontScale
    val prefetchStatus by viewModel.prefetchStatus
    val aiState by viewModel.aiAnswerState
    val questionPlaybackFinished by viewModel.questionPlaybackFinished
    val dueConcepts by viewModel.dueConceptQueue
    val currentConceptIdx by viewModel.currentConceptIndex
    val concept = viewModel.currentConcept
    val context = LocalContext.current
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startAiRecording()
        } else {
            viewModel.onAudioPermissionDenied()
        }
    }

    // Periodically refresh prefetch status
    LaunchedEffect(state, card) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            viewModel.updatePrefetchStatus()
        }
    }

    LaunchedEffect(gestureFeedback) {
        if (gestureFeedback != null) {
            kotlinx.coroutines.delay(800)
            viewModel.clearGestureFeedback()
        }
    }

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
                    onDoubleTap = { viewModel.onDoubleTap() },
                    onLongPress = { viewModel.onLongPress() }
                )
            }
            .detectAnkiAdvancedGestures(
                onSwipeLeft = { viewModel.onSwipeLeft() },
                onSwipeRight = { viewModel.onSwipeRight() },
                onSwipeUp = { viewModel.onSwipeUp() },
                onSwipeDown = { viewModel.onSwipeDown() },
                onTwoFingerTap = { viewModel.onTwoFingerTap() },
                onScaleChange = { viewModel.onScaleChange(it) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .padding(bottom = if (aiState.enabled && state == ReviewState.FRONT) 180.dp else 0.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            card?.let {
                if (state == ReviewState.FRONT) {
                    Text(
                        text = parseHtml(it.front).toAnnotatedString(),
                        fontSize = (24 * fontScale).sp,
                        textAlign = TextAlign.Center,
                        lineHeight = (32 * fontScale).sp
                    )
                } else if (state == ReviewState.BACK || state == ReviewState.CONCEPT_FRONT || state == ReviewState.CONCEPT_BACK) {
                    Text(
                        text = parseHtml(HtmlUtils.removeAnkiListenerConceptBlocks(it.back)).toAnnotatedString(),
                        fontSize = (22 * fontScale).sp,
                        textAlign = TextAlign.Center,
                        lineHeight = (30 * fontScale).sp
                    )
                }
            }
        }

        // Concept overlay dialog
        if ((state == ReviewState.CONCEPT_FRONT || state == ReviewState.CONCEPT_BACK) && concept != null) {
            // Get concept review state for color coding
            val conceptReviewState = viewModel.getCurrentConceptReviewState()
            val conceptHighlightColor = when (conceptReviewState?.lastEase) {
                ConceptReviewState.EASE_AGAIN -> MaterialTheme.colorScheme.error
                ConceptReviewState.EASE_HARD -> Color(0xFFFF9800) // Orange
                ConceptReviewState.EASE_GOOD -> MaterialTheme.colorScheme.primary
                ConceptReviewState.EASE_EASY -> Color(0xFF4CAF50) // Green
                else -> MaterialTheme.colorScheme.primary
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .padding(vertical = 32.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Progress badge
                        Text(
                            text = "概念 ${currentConceptIdx + 1}/${dueConcepts.size}",
                            fontSize = (13 * fontScale).sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (state == ReviewState.CONCEPT_FRONT) {
                            if (concept.title.isNotBlank()) {
                                Text(
                                    text = concept.title,
                                    fontSize = (18 * fontScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            Text(
                                text = parseConceptLinks(concept.question, conceptHighlightColor),
                                fontSize = (20 * fontScale).sp,
                                textAlign = TextAlign.Center,
                                lineHeight = (28 * fontScale).sp
                            )
                        } else {
                            // CONCEPT_BACK
                            Text(
                                text = parseConceptLinks(concept.question, conceptHighlightColor),
                                fontSize = (15 * fontScale).sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = (21 * fontScale).sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(
                                modifier = Modifier.fillMaxWidth(0.5f),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = parseConceptLinks(concept.answer, conceptHighlightColor),
                                fontSize = (20 * fontScale).sp,
                                textAlign = TextAlign.Center,
                                lineHeight = (28 * fontScale).sp
                            )
                        }
                    }
                }
            }
        }

        // Mark Button (subtle star icon at top right)
        card?.let {
            IconButton(
                onClick = { viewModel.toggleMark() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = if (it.isMarked) Icons.Default.Star else Icons.Outlined.Star,
                    contentDescription = "Mark Card",
                    tint = if (it.isMarked) Color(0xFFFFD700).copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        var lastFeedbackMessage by remember { mutableStateOf("") }
        if (gestureFeedback != null) {
            lastFeedbackMessage = gestureFeedback?.message ?: ""
        }

        AnimatedVisibility(
            visible = gestureFeedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 150.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = lastFeedbackMessage,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(
            visible = state == ReviewState.FRONT && card != null && aiState.enabled,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            AiAnswerPanel(
                state = aiState,
                questionPlaybackFinished = questionPlaybackFinished,
                onStartRecording = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.startAiRecording()
                    } else {
                        recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopRecording = { viewModel.stopAndSubmitAiRecording() },
                onCancelRecording = { viewModel.cancelAiRecording() }
            )
        }

        // Preload status indicator (bottom-right)
        if (prefetchStatus.prefetchCount > 0 && prefetchStatus.totalCards > 0) {
            val cached = (prefetchStatus.cachedFrontCount + prefetchStatus.cachedBackCount)
            val total = prefetchStatus.totalCards * 2 // front + back
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .background(
                        Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "\uD83D\uDD0A $cached/$total",
                    color = if (cached == total) Color(0xFF81C784) else Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun AiAnswerPanel(
    state: AiAnswerUiState,
    questionPlaybackFinished: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("AI 语音回答", style = MaterialTheme.typography.titleMedium)
            Text(
                text = when {
                    state.phase == AiAnswerPhase.RECORDING -> "正在录音中"
                    state.phase == AiAnswerPhase.SUBMITTING -> "正在分析回答"
                    state.followUpQuestion.isNotBlank() && !questionPlaybackFinished -> "追问：${state.followUpQuestion}"
                    state.followUpQuestion.isNotBlank() -> "追问：${state.followUpQuestion}（可开始录音）"
                    questionPlaybackFinished -> "问题朗读完成后即可开始录音"
                    else -> "等待问题朗读完成"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (state.phase == AiAnswerPhase.SUBMITTING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("正在提交并分析")
                }
            }

            if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.phase) {
                    AiAnswerPhase.RECORDING -> {
                        Button(onClick = onStopRecording) {
                            Icon(Icons.Filled.Send, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("停止并提交")
                        }
                        OutlinedButton(onClick = onCancelRecording) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("取消")
                        }
                    }
                    AiAnswerPhase.SUBMITTING -> {
                        OutlinedButton(onClick = {}, enabled = false) {
                            Text("处理中")
                        }
                    }
                    AiAnswerPhase.IDLE -> {
                        Button(
                            onClick = onStartRecording,
                            enabled = questionPlaybackFinished
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (state.followUpQuestion.isNotBlank()) "回答追问" else "开始录音")
                        }
                    }
                }
            }

            if (state.hasResult) {
                Divider()
                if (state.score != null) {
                    Text("评分：${state.score}")
                }
                if (state.transcript.isNotBlank()) {
                    Text("识别：${state.transcript}")
                }
                if (state.correction.isNotBlank()) {
                    Text("纠正：${state.correction}")
                }
                if (state.feedback.isNotBlank()) {
                    Text("反馈：${state.feedback}")
                }
                if (state.savedRecordPath != null) {
                    Text(
                        text = "已保存：${state.turnCount} 轮",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
