package com.ankilistener.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ankilistener.app.data.GestureAction
import com.ankilistener.app.data.GestureType
import com.ankilistener.app.ui.viewmodel.SettingsViewModel
import com.ankilistener.app.util.TtsProvider
import com.ankilistener.app.util.UpdateManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val mappings by viewModel.gestureMappings
    val ttsSettings by viewModel.ttsSettings
    val aiSettings by viewModel.aiSettings
    val conceptSettings by viewModel.conceptSettings
    val cacheStats by viewModel.cacheStats

    // Refresh cache stats when entering settings
    LaunchedEffect(Unit) {
        viewModel.refreshCacheStats()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ---- TTS Settings Section ----
            item {
                Text(
                    "语音合成 (TTS)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // Provider Switch
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("使用在线TTS接口", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = ttsSettings.provider == TtsProvider.API,
                        onCheckedChange = { isApi ->
                            viewModel.updateTtsProvider(if (isApi) TtsProvider.API else TtsProvider.SYSTEM)
                        }
                    )
                }
            }

            // Skip Question on Back toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("背面自动跳过问题", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "在显示答案时，不重复朗读卡片正面的问题内容",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = ttsSettings.skipQuestionOnBack,
                        onCheckedChange = { viewModel.updateSkipQuestionOnBack(it) }
                    )
                }
            }

            // API Settings (only visible when API provider is selected)
            if (ttsSettings.provider == TtsProvider.API) {
                item {
                    TtsTextField(
                        label = "服务器地址",
                        value = ttsSettings.baseUrl,
                        placeholder = "http://172.22.64.1:3000",
                        onValueChange = { viewModel.updateTtsBaseUrl(it) }
                    )
                }
                item {
                    TtsTextField(
                        label = "语速 (speakSpeed)",
                        value = ttsSettings.speed,
                        placeholder = "1.0",
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = { viewModel.updateTtsSpeed(it) }
                    )
                }
                item {
                    TtsTextField(
                        label = "延迟 (delay)",
                        value = ttsSettings.delay,
                        placeholder = "5",
                        keyboardType = KeyboardType.Number,
                        onValueChange = { viewModel.updateTtsDelay(it) }
                    )
                }
                item {
                    TtsTextField(
                        label = "音色 (voice)",
                        value = ttsSettings.voice,
                        placeholder = "zh_female_wenroutaozi_uranus_bigtts",
                        onValueChange = { viewModel.updateTtsVoice(it) }
                    )
                }
                item {
                    Text(
                        "兼容阅读(legado)的TTS接口格式",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // ---- Prefetch & Cache Section ----
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }

                item {
                    Text(
                        "预加载与缓存",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                // Prefetch count slider
                item {
                    var textValue by remember(ttsSettings.prefetchCount) {
                        mutableStateOf(ttsSettings.prefetchCount.toString())
                    }
                    TtsTextField(
                        label = "预加载卡片数",
                        value = textValue,
                        placeholder = "3",
                        keyboardType = KeyboardType.Number,
                        onValueChange = { newValue ->
                            // Allow digits only
                            if (newValue.all { it.isDigit() }) {
                                textValue = newValue
                                val count = newValue.toIntOrNull()
                                if (count != null) {
                                    viewModel.updatePrefetchCount(count)
                                } else if (newValue.isEmpty()) {
                                    viewModel.updatePrefetchCount(0)
                                }
                            }
                        }
                    )

                    Text(
                        "提前下载接下来的卡片音频，0 = 不预加载",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }


                // Cache stats
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("缓存统计", style = MaterialTheme.typography.titleSmall)
                                Row {
                                    IconButton(
                                        onClick = { viewModel.refreshCacheStats() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "刷新",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "已缓存音频",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${cacheStats.fileCount} 条",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "占用空间",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${cacheStats.totalSizeMB} MB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.clearCache() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("清除所有缓存")
                            }
                        }
                    }
                }
            }

            // ---- Divider between sections ----
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // ---- AI Answer Settings Section ----
            item {
                Text(
                    "AI 回答",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用 AI 语音回答", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "问题朗读完成后，可录音提交给 AI 评分、纠正并生成追问",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = aiSettings.enabled,
                        onCheckedChange = { viewModel.updateAiEnabled(it) }
                    )
                }
            }

            if (aiSettings.enabled) {
                item {
                    TtsTextField(
                        label = "AI 接口地址",
                        value = aiSettings.endpoint,
                        placeholder = "http://172.22.64.1:3000/api/anki-listener/answer",
                        onValueChange = { viewModel.updateAiEndpoint(it) }
                    )
                }
                item {
                    TtsTextField(
                        label = "API Key",
                        value = aiSettings.apiKey,
                        placeholder = "可留空",
                        onValueChange = { viewModel.updateAiApiKey(it) }
                    )
                }
                item {
                    TtsTextField(
                        label = "模型",
                        value = aiSettings.model,
                        placeholder = "default",
                        onValueChange = { viewModel.updateAiModel(it) }
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("允许 AI 追问", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "追问与回答会保存在本地记录中，不同步到 Anki",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = aiSettings.followUpEnabled,
                            onCheckedChange = { viewModel.updateAiFollowUpEnabled(it) }
                        )
                    }
                }
                item {
                    Text(
                        "接口需接收 JSON：card、prompt、audio.base64、turnHistory；返回 transcript、score、feedback、correction、followUpQuestion。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                item {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            // ---- Concept Review Settings Section ----
            item {
                Text(
                    "子概念复习",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用子概念复习", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "主卡背面嵌入的子概念会在复习时追问",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = conceptSettings.enabled,
                        onCheckedChange = { viewModel.updateConceptEnabled(it) }
                    )
                }
            }

            if (conceptSettings.enabled) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("只复习到期子概念", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "关闭时每次主卡都追问所有子概念",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = conceptSettings.dueOnly,
                            onCheckedChange = { viewModel.updateConceptDueOnly(it) }
                        )
                    }
                }

                item {
                    var textValue by remember(conceptSettings.againDelayMinutes) {
                        mutableStateOf(conceptSettings.againDelayMinutes.toString())
                    }
                    TtsTextField(
                        label = "Again 延迟 (分钟)",
                        value = textValue,
                        placeholder = "10",
                        keyboardType = KeyboardType.Number,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                textValue = newValue
                                val minutes = newValue.toIntOrNull()
                                if (minutes != null && minutes > 0) {
                                    viewModel.updateConceptAgainDelayMinutes(minutes)
                                }
                            }
                        }
                    )
                    Text(
                        "子概念选择\"重来\"后多久再次出现",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // ---- Gesture Settings Section ----
            item {
                Text(
                    "手势设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            items(GestureAction.values().filter { it != GestureAction.NONE && it != GestureAction.MARK }) { action ->
                val currentGesture = mappings[action] ?: GestureType.NONE
                ActionSettingItem(
                    action = action,
                    currentGesture = currentGesture,
                    onGestureSelected = { newGesture ->
                        viewModel.updateActionGesture(action, newGesture)
                    }
                )
                Divider()
            }

            // ---- Version Info ----
            item {
                Spacer(modifier = Modifier.height(24.dp))
                val context = LocalContext.current
                val version = remember { UpdateManager.getCurrentVersion(context) }
                Text(
                    text = "AnkiListener v$version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun TtsTextField(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
fun ActionSettingItem(
    action: GestureAction,
    currentGesture: GestureType,
    onGestureSelected: (GestureType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val actionName = getActionName(action)
    val gestureName = getGestureName(currentGesture)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(actionName, style = MaterialTheme.typography.bodyLarge)
            Text(gestureName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            GestureType.values().forEach { gesture ->
                DropdownMenuItem(
                    text = { Text(getGestureName(gesture)) },
                    onClick = {
                        onGestureSelected(gesture)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun getGestureName(gesture: GestureType): String {
    return when (gesture) {
        GestureType.NONE -> "无手势"
        GestureType.SINGLE_TAP -> "单击屏幕"
        GestureType.DOUBLE_TAP -> "双击屏幕"
        GestureType.SWIPE_LEFT -> "向左滑动"
        GestureType.SWIPE_RIGHT -> "向右滑动"
        GestureType.SWIPE_UP -> "向上滑动"
        GestureType.SWIPE_DOWN -> "向下滑动"
        GestureType.LONG_PRESS -> "长按屏幕"
        GestureType.TWO_FINGER_TAP -> "双指点击"
    }
}

private fun getActionName(action: GestureAction): String {
    return when (action) {
        GestureAction.NONE -> "无"
        GestureAction.SHOW_ANSWER -> "显示答案 (正面有效)"
        GestureAction.PLAY_TTS -> "播放发音"
        GestureAction.ANSWER_AGAIN -> "重来 (背面有效)"
        GestureAction.ANSWER_HARD -> "困难 (背面有效)"
        GestureAction.ANSWER_GOOD -> "良好 (背面有效)"
        GestureAction.ANSWER_EASY -> "简单 (背面有效)"
        GestureAction.SKIP -> "跳过"
        GestureAction.MARK -> "标记 (已移至卡片按钮)"
        GestureAction.UNDO -> "撤销"
    }
}
