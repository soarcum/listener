package com.ankilistener.app.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 增强型手势监听：支持左右滑动、双指点击、缩放 (Passive 模式，不阻塞滚动)
 */
fun Modifier.detectAnkiAdvancedGestures(
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onTwoFingerTap: () -> Unit = {},
    onScaleChange: (Float) -> Unit = {}
): Modifier = this.pointerInput(Unit) {
    val swipeThresholdPx = 40.dp.toPx()
    
    awaitEachGesture {
        awaitFirstDown(pass = PointerEventPass.Initial)
        var totalDragX = 0f
        var totalDragY = 0f
        var maxPointers = 1
        
        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val pointersCount = event.changes.size
            if (pointersCount > maxPointers) maxPointers = pointersCount

            // 1. 处理缩放
            if (pointersCount >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onScaleChange(zoom)
                }
            }

            // 2. 处理滑动位移 (仅单指时有效)
            val change = event.changes.firstOrNull()
            if (change != null) {
                totalDragX += change.positionChange().x
                totalDragY += change.positionChange().y
            }
        } while (event.changes.any { it.pressed })

        // 3. 抬起结算
        val absX = abs(totalDragX)
        val absY = abs(totalDragY)

        if (maxPointers == 2 && absX < 30 && absY < 30) {
            // 双指点击：曾出现过两指，且最终位移不大
            onTwoFingerTap()
        } else if (maxPointers == 1) {
            // 单指滑动
            if (absX > swipeThresholdPx && absX > absY) {
                if (totalDragX > 0) onSwipeRight() else onSwipeLeft()
            }
        }
    }
}
