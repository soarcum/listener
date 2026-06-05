package com.ankilistener.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
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
import com.ankilistener.app.data.ReviewOrder
import com.ankilistener.app.data.ThemeMode
import com.ankilistener.app.data.TtsScheme
import com.ankilistener.app.data.TtsSchemeItem
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
    val themeMode by viewModel.themeMode

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
            // ---- Theme Settings Section ----
            item {
                Text(
                    "外观",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val options = listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "浅色",
                        ThemeMode.DARK to "深色"
                    )
                    options.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { viewModel.updateThemeMode(mode) },
                            selected = themeMode == mode
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // ---- TTS Settings Section ----
            item {
                Text(
                    "语音合成 (TTS)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // Scheme Selection: System TTS / Online API
            item {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val options = listOf(
                        TtsScheme.SYSTEM to "系统TTS",
                        TtsScheme.API to "在线API"
                    )
                    options.forEachIndexed { index, (scheme, label) ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { viewModel.updateTtsScheme(scheme) },
                            selected = ttsSettings.scheme == scheme
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            // API Scheme List (only when API is selected)
            if (ttsSettings.scheme == TtsScheme.API) {
                item {
                    TtsSchemeListCard(viewModel, ttsSettings)
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
                            Text("主卡背面分段展示", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "按 (背景)、(任务) 等标签分段揭开主卡",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = conceptSettings.segmentedResponseEnabled,
                            onCheckedChange = { viewModel.updateConceptSegmentedResponseEnabled(it) }
                        )
                    }
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

                item {
                    Text(
                        "复习顺序",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    var expanded by remember { mutableStateOf(false) }
                    val orderName = when (conceptSettings.reviewOrder) {
                        ReviewOrder.MAIN_CONCEPT_FOLLOWUP -> "主卡片 -> 概念 -> 追问"
                        ReviewOrder.CONCEPT_MAIN_FOLLOWUP -> "概念 -> 主卡片 -> 追问"
                        ReviewOrder.FOLLOWUP_CONCEPT_MAIN -> "追问 -> 概念 -> 主卡片"
                        ReviewOrder.INTERLEAVED -> "交织模式 (按片段和概念顺序)"
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(orderName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            val options = listOf(
                                ReviewOrder.MAIN_CONCEPT_FOLLOWUP to "主卡片 -> 概念 -> 追问",
                                ReviewOrder.CONCEPT_MAIN_FOLLOWUP to "概念 -> 主卡片 -> 追问",
                                ReviewOrder.FOLLOWUP_CONCEPT_MAIN to "追问 -> 概念 -> 主卡片",
                                ReviewOrder.INTERLEAVED to "交织模式 (按片段和概念顺序)"
                            )
                            options.forEach { (order, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        viewModel.updateReviewOrder(order)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
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
private fun TtsSchemeListCard(
    viewModel: SettingsViewModel,
    ttsSettings: com.ankilistener.app.ui.viewmodel.TtsSettings
) {
    var showInput by remember { mutableStateOf(false) }
    var editingSchemeId by remember { mutableStateOf<String?>(null) }
    var editedName by remember { mutableStateOf("") }
    var editedAddress by remember { mutableStateOf("") }
    var editedKey by remember { mutableStateOf("") }
    var editedVoice by remember { mutableStateOf("mimo_default") }
    var editedStylePrompt by remember { mutableStateOf("") }

    LaunchedEffect(editingSchemeId) {
        val scheme = editingSchemeId?.let { id -> ttsSettings.schemes.find { it.id == id } }
        if (scheme != null) {
            editedName = scheme.name
            editedAddress = scheme.address
            editedKey = scheme.apiKey
            editedVoice = scheme.voice
            editedStylePrompt = scheme.stylePrompt
        } else if (showInput) {
            editedName = ""
            editedAddress = ""
            editedKey = ""
            editedVoice = "mimo_default"
            editedStylePrompt = "Natural and clear English speech, standard pace and friendly tone."
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // ---- Scheme List ----
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已保存方案", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        editingSchemeId = null
                        showInput = true
                    }) {
                        Text("+ 添加")
                    }
                }

                if (ttsSettings.schemes.isEmpty() && !showInput) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "还没有保存的方案，点击\"+ 添加\"创建",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                ttsSettings.schemes.forEachIndexed { index, scheme ->
                    if (index > 0) Spacer(modifier = Modifier.height(4.dp))
                    val isSelected = scheme.id == ttsSettings.activeSchemeId
                    SchemeRow(
                        scheme = scheme,
                        isSelected = isSelected,
                        onSelect = { viewModel.selectScheme(scheme.id) },
                        onEdit = {
                            editingSchemeId = scheme.id
                            showInput = true
                        },
                        onDelete = { viewModel.removeScheme(scheme.id) }
                    )
                }

                // Input fields for add/edit
                if (showInput) {
                    if (ttsSettings.schemes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (editingSchemeId == null) {
                        Text("选择预设模版", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SuggestionChip(
                                onClick = {
                                    editedName = "小米 TTS"
                                    editedAddress = "https://api.xiaomimimo.com/v1/chat/completions"
                                    editedVoice = "mimo_default"
                                    editedStylePrompt = "Natural and clear English speech, standard pace and friendly tone."
                                },
                                label = { Text("小米 TTS") }
                            )
                            SuggestionChip(
                                onClick = {
                                    editedName = "默认 Legado"
                                    editedAddress = "http://172.22.64.1:3000"
                                    editedVoice = "zh_female_wenroutaozi_uranus_bigtts"
                                    editedStylePrompt = ""
                                },
                                label = { Text("默认 Legado") }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("方案名称") },
                        placeholder = { Text("如：家里服务器") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedAddress,
                        onValueChange = { editedAddress = it },
                        label = { Text("服务器地址") },
                        placeholder = { Text("基准URL 或 包含占位符的完整URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "提示：支持直接粘贴阅读APP的自定义TTS链接（包含 {{speakText}} 占位符）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedKey,
                        onValueChange = { editedKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("可留空") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedVoice,
                        onValueChange = { editedVoice = it },
                        label = { Text("音色 (voice)") },
                        placeholder = { Text("如: mimo_default") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (editedAddress.contains("api.xiaomimimo.com")) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editedStylePrompt,
                            onValueChange = { editedStylePrompt = it },
                            label = { Text("音色描述 (stylePrompt)") },
                            placeholder = { Text("如: Natural speech, clear pace.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (editedAddress.isNotBlank()) {
                                val id = editingSchemeId
                                if (id != null) {
                                    viewModel.updateScheme(
                                        ttsSettings.schemes.find { it.id == id }!!.copy(
                                            name = editedName.trim().ifBlank { "未命名" },
                                            address = editedAddress.trim(),
                                            apiKey = editedKey.trim(),
                                            voice = editedVoice.trim(),
                                            stylePrompt = editedStylePrompt.trim()
                                        )
                                    )
                                } else {
                                    viewModel.addScheme(
                                        TtsSchemeItem(
                                            name = editedName.trim().ifBlank { "未命名" },
                                            address = editedAddress.trim(),
                                            apiKey = editedKey.trim(),
                                            voice = editedVoice.trim(),
                                            stylePrompt = editedStylePrompt.trim()
                                        )
                                    )
                                }
                                showInput = false
                                editingSchemeId = null
                            }
                        },
                        enabled = editedAddress.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("确认")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            showInput = false
                            editingSchemeId = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消")
                    }
                }
            }
        }

        // ---- Active Scheme Config ----
        val active = ttsSettings.activeScheme
        if (active != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TtsTextField(
                label = "语速 (speakSpeed)",
                value = active.speed,
                placeholder = "1.0",
                keyboardType = KeyboardType.Decimal,
                onValueChange = { viewModel.updateActiveSchemeSpeed(it) }
            )
            TtsTextField(
                label = "延迟 (delay)",
                value = active.delay,
                placeholder = "5",
                keyboardType = KeyboardType.Number,
                onValueChange = { viewModel.updateActiveSchemeDelay(it) }
            )
            TtsTextField(
                label = "音色 (voice)",
                value = active.voice,
                placeholder = "zh_female_wenroutaozi_uranus_bigtts",
                onValueChange = { viewModel.updateActiveSchemeVoice(it) }
            )
            if (active.address.contains("api.xiaomimimo.com")) {
                TtsTextField(
                    label = "音色描述 (stylePrompt)",
                    value = active.stylePrompt,
                    placeholder = "如: Natural and clear English speech.",
                    onValueChange = { viewModel.updateActiveSchemeStylePrompt(it) }
                )
            }
            Text(
                if (active.address.contains("api.xiaomimimo.com")) "小米 TTS 接口格式" else "兼容阅读(legado)的TTS接口格式",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Prefetch & Cache
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "预加载与缓存",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            var prefetchText by remember(ttsSettings.prefetchCount) {
                mutableStateOf(ttsSettings.prefetchCount.toString())
            }
            TtsTextField(
                label = "预加载卡片数",
                value = prefetchText,
                placeholder = "3",
                keyboardType = KeyboardType.Number,
                onValueChange = { newValue ->
                    if (newValue.all { it.isDigit() }) {
                        prefetchText = newValue
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
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            CacheStatsCard(viewModel)
        }
    }
}

@Composable
private fun SchemeRow(
    scheme: TtsSchemeItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(scheme.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                scheme.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Create, contentDescription = "编辑", modifier = Modifier.size(18.dp))
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "更多", modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = { onDelete(); menuExpanded = false }
                )
            }
        }
    }
}

@Composable
private fun CacheStatsCard(viewModel: SettingsViewModel) {
    val cacheStats by viewModel.cacheStats

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("缓存统计", style = MaterialTheme.typography.titleSmall)
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
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("已缓存音频", style = MaterialTheme.typography.bodyMedium)
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
                Text("占用空间", style = MaterialTheme.typography.bodyMedium)
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
            .padding(vertical = 4.dp)
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
