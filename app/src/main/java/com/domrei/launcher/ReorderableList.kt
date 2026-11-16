package com.domrei.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/**
 * Reorders a list by moving an element from [fromIndex] to [toIndex].
 * Returns a new list with the updated ordering.
 */
fun <T> List<T>.reorder(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    val list = toMutableList()
    val item = list.removeAt(fromIndex)
    list.add(toIndex, item)
    return list
}

class ReorderableState(
    val listState: LazyListState,
    private val onMoveCallback: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragStartCallback: (() -> Unit)? = null,
    private val onDragEndCallback: (() -> Unit)? = null,
) {
    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    var draggedOffset by mutableFloatStateOf(0f)
        private set

    fun onDragStart(index: Int) {
        draggedIndex = index
        draggedOffset = 0f
        onDragStartCallback?.invoke()
    }

    fun onDrag(dragAmountY: Float) {
        if (draggedIndex == null) return
        draggedOffset += dragAmountY
        checkSwap()
    }

    fun adjustOffsetForScroll(delta: Float) {
        draggedOffset += delta
    }

    fun checkSwap() {
        val currentIndex = draggedIndex ?: return
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.firstOrNull { it.index == currentIndex } ?: return

        val draggedCenter = currentItem.offset + currentItem.size / 2f + draggedOffset

        val targetItem = if (draggedOffset > 0) {
            visibleItems
                .filter { it.index > currentIndex && draggedCenter >= (it.offset + it.size / 2f) }
                .maxByOrNull { it.index }
        } else if (draggedOffset < 0) {
            visibleItems
                .filter { it.index < currentIndex && draggedCenter <= (it.offset + it.size / 2f) }
                .minByOrNull { it.index }
        } else {
            null
        }

        if (targetItem != null) {
            val targetIndex = targetItem.index
            onMoveCallback(currentIndex, targetIndex)
            draggedOffset -= (targetItem.offset - currentItem.offset)
            draggedIndex = targetIndex
        }
    }

    fun onDragEnd() {
        draggedIndex = null
        draggedOffset = 0f
        onDragEndCallback?.invoke()
    }

    fun onDragCancel() {
        draggedIndex = null
        draggedOffset = 0f
        onDragEndCallback?.invoke()
    }
}

@Composable
fun rememberReorderableState(
    listState: LazyListState,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
): ReorderableState {
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)

    val state = remember(listState) {
        ReorderableState(
            listState = listState,
            onMoveCallback = { from, to -> currentOnMove(from, to) },
            onDragStartCallback = { currentOnDragStart?.invoke() },
            onDragEndCallback = { currentOnDragEnd?.invoke() },
        )
    }

    // Auto-scroll when dragging near viewport boundaries
    LaunchedEffect(state.draggedIndex) {
        if (state.draggedIndex == null) return@LaunchedEffect
        while (state.draggedIndex != null) {
            val currentIndex = state.draggedIndex ?: break
            val layoutInfo = state.listState.layoutInfo
            val currentItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            if (currentItem != null) {
                val viewportStart = layoutInfo.viewportStartOffset
                val viewportEnd = layoutInfo.viewportEndOffset
                val currentTop = currentItem.offset + state.draggedOffset
                val currentBottom = currentTop + currentItem.size
                val threshold = 70f
                val scrollDelta = when {
                    currentTop < viewportStart + threshold -> {
                        val ratio = ((viewportStart + threshold - currentTop) / threshold).coerceIn(0f, 1f)
                        -ratio * 16f
                    }
                    currentBottom > viewportEnd - threshold -> {
                        val ratio = ((currentBottom - (viewportEnd - threshold)) / threshold).coerceIn(0f, 1f)
                        ratio * 16f
                    }
                    else -> 0f
                }
                if (scrollDelta != 0f) {
                    state.listState.scrollBy(scrollDelta)
                    state.adjustOffsetForScroll(scrollDelta)
                    state.checkSwap()
                }
            }
            delay(16)
        }
    }

    return state
}

/**
 * Modifier applied to each item in a reorderable list.
 * Listens for long-press to initiate dragging, tracks pointer motion,
 * and translates the active item vertically.
 */
fun Modifier.reorderableItem(
    state: ReorderableState,
    key: Any,
    index: Int,
    enabled: Boolean = true,
    haptic: HapticFeedback? = null,
    soundPlayer: LauncherSoundPlayer? = null,
): Modifier = composed {
    val isDragging = state.draggedIndex == index
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "reorderScale",
    )

    this
        .zIndex(if (isDragging) 3f else 0f)
        .graphicsLayer {
            translationY = if (isDragging) state.draggedOffset else 0f
            scaleX = scale
            scaleY = scale
            shadowElevation = if (isDragging) 16f else 0f
        }
        .then(
            if (enabled) {
                Modifier.pointerInput(key) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
                            soundPlayer?.playBobble()
                            state.onDragStart(index)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            state.onDrag(dragAmount.y)
                        },
                        onDragEnd = {
                            state.onDragEnd()
                        },
                        onDragCancel = {
                            state.onDragCancel()
                        },
                    )
                }
            } else {
                Modifier
            }
        )
}
