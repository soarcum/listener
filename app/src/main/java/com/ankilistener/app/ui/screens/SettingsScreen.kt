package com.ankilistener.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ankilistener.app.data.GestureAction
import com.ankilistener.app.data.GestureType
import com.ankilistener.app.ui.viewmodel.SettingsViewModel
import com.ankilistener.app.util.TtsProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val mappings by viewModel.gestureMappings
    val ttsSettings by viewModel.ttsSettings

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
            }

            // ---- Divider between sections ----
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

            items(GestureAction.values().filter { it != GestureAction.NONE }) { action ->
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
        GestureAction.MARK -> "标记"
        GestureAction.UNDO -> "撤销"
    }
}
