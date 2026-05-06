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
                title = { Text("手势设置 (v1.1)") },
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
            items(GestureType.values()) { gestureType ->
                val currentAction = mappings[gestureType] ?: GestureAction.NONE
                GestureSettingItem(
                    gestureType = gestureType,
                    currentAction = currentAction,
                    onActionSelected = { newAction ->
                        viewModel.updateGestureAction(gestureType, newAction)
                    }
                )
                Divider()
            }
        }
    }
}

@Composable
fun GestureSettingItem(
    gestureType: GestureType,
    currentAction: GestureAction,
    onActionSelected: (GestureAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val gestureName = when (gestureType) {
        GestureType.SINGLE_TAP -> "单击屏幕"
        GestureType.DOUBLE_TAP -> "双击屏幕"
        GestureType.SWIPE_LEFT -> "向左滑动"
        GestureType.SWIPE_RIGHT -> "向右滑动"
        GestureType.SWIPE_UP -> "向上滑动"
        GestureType.SWIPE_DOWN -> "向下滑动"
    }

    val actionName = getActionName(currentAction)

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
            Text(gestureName, style = MaterialTheme.typography.bodyLarge)
            Text(actionName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            GestureAction.values().forEach { action ->
                DropdownMenuItem(
                    text = { Text(getActionName(action)) },
                    onClick = {
                        onActionSelected(action)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun getActionName(action: GestureAction): String {
    return when (action) {
        GestureAction.NONE -> "无动作"
        GestureAction.SHOW_ANSWER -> "显示答案"
        GestureAction.PLAY_TTS -> "播放发音"
        GestureAction.ANSWER_AGAIN -> "重来 (1)"
        GestureAction.ANSWER_HARD -> "困难 (2)"
        GestureAction.ANSWER_GOOD -> "良好 (3)"
        GestureAction.ANSWER_EASY -> "简单 (4)"
        GestureAction.SKIP -> "跳过 (不记录)"
        GestureAction.MARK -> "标记 (Mark)"
    }
}
