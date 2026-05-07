package com.ankilistener.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ankilistener.app.data.GestureAction
import com.ankilistener.app.data.GestureType
import com.ankilistener.app.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val mappings by viewModel.gestureMappings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("手势设置") },
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
