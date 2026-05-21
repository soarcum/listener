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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ankilistener.app.ui.viewmodel.AiAnswerPhase
import com.ankilistener.app.ui.viewmodel.AiAnswerUiState
import com.ankilistener.app.ui.viewmodel.ReviewState
import com.ankilistener.app.ui.viewmodel.ReviewViewModel
import com.ankilistener.app.util.HtmlUtils
import com.ankilistener.app.util.HtmlUtils.toAnnotatedString
import com.ankilistener.app.util.HtmlUtils.parseHtml
import com.ankilistener.app.util.HtmlUtils.parseConceptLinks
import com.ankilistener.app.util.HtmlUtils.parseConceptLinksWithFocus

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
    val dueFollowUps by viewModel.dueFollowUpQueue
    val currentFollowUpIdx by viewModel.currentFollowUpIndex
    val followUp = viewModel.currentFollowUp
    val isSubconceptActive = state == ReviewState.CONCEPT_FRONT || 
                             state == ReviewState.CONCEPT_BACK || 
                             state == ReviewState.FOLLOWUP_FRONT || 
                             state == ReviewState.FOLLOWUP_BACK
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

    if (state == ReviewState.LOADING) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (state == ReviewState.NO_CARDS) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("今日无待复习卡片", style = MaterialTheme.typography.headlineMedium)
        }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            onFinished()
        }
        return
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
            verticalArrangement = if (isSubconceptActive) Arrangement.Top else Arrangement.Center
        ) {
            if (isSubconceptActive) {
                Spacer(modifier = Modifier.height(16.dp))
            }
            card?.let {
                if (state == ReviewState.FRONT) {
                    Text(
                        text = parseHtml(it.front).toAnnotatedString(),
                        fontSize = (24 * fontScale).sp,
                        textAlign = TextAlign.Center,
                        lineHeight = (32 * fontScale).sp
                    )
                } else if (state == ReviewState.BACK || state == ReviewState.CONCEPT_FRONT || state == ReviewState.CONCEPT_BACK || state == ReviewState.FOLLOWUP_FRONT || state == ReviewState.FOLLOWUP_BACK) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. 背景问题显示：在子概念激活时，不仅变小，透明度也同步降低，退居极次要层
                        Text(
                            text = parseHtml(it.front).toAnnotatedString(),
                            fontSize = if (isSubconceptActive) (12 * fontScale).sp else (16 * fontScale).sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = if (isSubconceptActive) (17 * fontScale).sp else (22 * fontScale).sp,
                            modifier = Modifier.alpha(if (isSubconceptActive) 0.3f else 1.0f)
                        )
                        Spacer(modifier = Modifier.height(if (isSubconceptActive) 10.dp else 24.dp))
                        Divider(
                            modifier = Modifier
                                .fillMaxWidth(if (isSubconceptActive) 0.3f else 0.6f)
                                .alpha(if (isSubconceptActive) 0.2f else 1.0f),
                            thickness = 0.8.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(modifier = Modifier.height(if (isSubconceptActive) 10.dp else 24.dp))

                        // 2. 背景答案渲染：提取主答案文本
                        val answerOnly = HtmlUtils.extractAnswerOnlyHtml(HtmlUtils.removeAnkiListenerConceptBlocks(it.back))
                        val conceptColorMap = viewModel.getConceptColorMap()
                        val defaultColor = MaterialTheme.colorScheme.primary

                        if (isSubconceptActive) {
                            // 【优化重点】当子概念或追问复习处于激活状态时，开启行级精细化排版
                            val answerLines = answerOnly.split("\n").filter { it.isNotBlank() }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy((4 * fontScale).dp) // 极窄行距，压缩垂直空间
                            ) {
                                answerLines.forEach { line ->
                                    // 匹配逻辑：判断当前段落行是否与正在复习的子概念相关
                                    var isCurrentActiveLine = false
                                    if ((state == ReviewState.CONCEPT_FRONT || state == ReviewState.CONCEPT_BACK) && concept != null) {
                                        val conceptTitle = concept.title
                                        val conceptId = concept.id
                                        // 若行中包含当前子概念的 Title 或 ID，则视为当前活跃段落
                                        isCurrentActiveLine = (conceptTitle.isNotBlank() && line.contains(conceptTitle)) || line.contains(conceptId)
                                    }

                                    // 动态计算行级样式：非当前子概念行（例如在复习“行动”时，原本的“背景”、“难点”行）字号压缩至 10.sp，透明度压低至 0.18f（极淡）
                                    // 正在复习的子概念行（如“行动”行）则维持 14.sp 并且给予 0.85f 清晰度，同时加粗显示以提示上下文定位
                                    val lineSize = if (isCurrentActiveLine) (14 * fontScale).sp else (10 * fontScale).sp
                                    val lineAlpha = if (isCurrentActiveLine) 0.85f else 0.18f
                                    val lineLineHeight = if (isCurrentActiveLine) (19 * fontScale).sp else (14 * fontScale).sp
                                    val lineWeight = if (isCurrentActiveLine) FontWeight.Medium else FontWeight.Normal

                                    Text(
                                        text = parseConceptLinks(line, defaultColor, conceptColorMap),
                                        fontSize = lineSize,
                                        fontWeight = lineWeight,
                                        textAlign = TextAlign.Center,
                                        lineHeight = lineLineHeight,
                                        modifier = Modifier.alpha(lineAlpha)
                                    )
                                }
                            }
                        } else {
                            // 【常规复习】当没有子概念激活时，100% 保持原有主卡片的常规与分段聚焦渲染，不影响普通复习
                            val revealSteps by viewModel.revealSteps
                            val currentSegmentStep by viewModel.currentSegmentStep
                            val showSegmentedFocus = revealSteps.isNotEmpty() && currentSegmentStep < revealSteps.size

                            if (showSegmentedFocus) {
                                val ttsSteps by viewModel.ttsSteps
                                val idx = currentSegmentStep
                                val prefixText = if (ttsSteps.isNotEmpty() && idx < ttsSteps.size) {
                                    val currentSeg = ttsSteps[idx]
                                    val isCurrLabel = currentSeg.trim().let {
                                        (it.startsWith("(") && it.endsWith(")")) || (it.startsWith("（") && it.endsWith("）"))
                                    }
                                    val prefixIdx = if (isCurrLabel) idx - 1 else idx - 2
                                    if (prefixIdx >= 0 && prefixIdx < revealSteps.size) {
                                        revealSteps[prefixIdx]
                                    } else {
                                        ""
                                    }
                                } else {
                                    ""
                                }

                                val fullText = revealSteps[idx]
                                val focusedText = if (fullText.startsWith(prefixText)) {
                                    fullText.substring(prefixText.length)
                                } else {
                                    fullText
                                }

                                Text(
                                    text = parseConceptLinksWithFocus(
                                        prefixText = prefixText,
                                        currentText = focusedText,
                                        defaultColor = defaultColor,
                                        conceptColorMap = conceptColorMap,
                                        fadedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    fontSize = (22 * fontScale).sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = (30 * fontScale).sp
                                )
                            } else {
                                Text(
                                    text = parseConceptLinks(answerOnly, defaultColor, conceptColorMap),
                                    fontSize = (22 * fontScale).sp,
                                    textAlign = TextAlign.Center,
                                    lineHeight = (30 * fontScale).sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Concept overlay dialog
        if ((state == ReviewState.CONCEPT_FRONT || state == ReviewState.CONCEPT_BACK) && concept != null) {
            val conceptColorMap = viewModel.getConceptColorMap()
            val defaultColor = MaterialTheme.colorScheme.primary

            SegmentedReviewOverlay(
                progressText = "概念 ${currentConceptIdx + 1}/${dueConcepts.size}",
                titleText = concept.title,
                questionText = concept.question,
                answerText = if (state == ReviewState.CONCEPT_BACK) concept.answer else null,
                fontScale = fontScale,
                conceptColorMap = conceptColorMap,
                defaultColor = defaultColor
            )
        }

        // Follow-up overlay dialog
        if ((state == ReviewState.FOLLOWUP_FRONT || state == ReviewState.FOLLOWUP_BACK) && followUp != null) {
            val conceptColorMap = viewModel.getConceptColorMap()
            val defaultColor = MaterialTheme.colorScheme.primary

            SegmentedReviewOverlay(
                progressText = "追问 ${currentFollowUpIdx + 1}/${dueFollowUps.size}",
                questionText = followUp.question,
                answerText = if (state == ReviewState.FOLLOWUP_BACK) followUp.answer else null,
                fontScale = fontScale,
                conceptColorMap = conceptColorMap,
                defaultColor = defaultColor
            )
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

/**
 * 通用的分段复习与追问卡片浮层组件
 * 经过精细化的尺寸缩小与排版优化，提供低视觉抢占、优雅小巧的现代化界面。
 */
@Composable
private fun SegmentedReviewOverlay(
    progressText: String,
    titleText: String = "",
    questionText: String,
    answerText: String? = null,
    fontScale: Float,
    conceptColorMap: Map<String, Color>,
    defaultColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)), // 降低遮罩透明度，让底色更柔和
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.82f) // 精巧的卡片宽度
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(12.dp), // 更精细现代的圆角
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 18.dp) // 精简内边距
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 精致的胶囊状态进度徽章
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        text = progressText,
                        fontSize = (11 * fontScale).sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                // 子概念标题（仅在有值时显示）
                if (titleText.isNotBlank()) {
                    Text(
                        text = titleText,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        fontSize = (15 * fontScale).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }

                if (answerText == null) {
                    // 提问状态 (FRONT)
                    Text(
                        text = parseConceptLinks(questionText, defaultColor, conceptColorMap),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = (16 * fontScale).sp,
                        textAlign = TextAlign.Center,
                        lineHeight = (22 * fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    // 回答与解析状态 (BACK)
                    // 1. 已提问的问题（字号进一步缩小，降低视觉抢占）
                    Text(
                        text = parseConceptLinks(questionText, defaultColor, conceptColorMap),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = (12 * fontScale).sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        lineHeight = (17 * fontScale).sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. 极细且窄的优雅分割线
                    Divider(
                        modifier = Modifier.fillMaxWidth(0.4f),
                        thickness = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. 行动/概念的答案
                    Text(
                        text = parseConceptLinks(answerText, defaultColor, conceptColorMap),
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = (16 * fontScale).sp,
                        textAlign = TextAlign.Center,
                        lineHeight = (22 * fontScale).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

