package com.domrei.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val totalItemsCount: () -> Int,
    private val onMoveCallback: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragStartCallback: (() -> Unit)? = null,
    private val onDragEndCallback: (() -> Unit)? = null,
    private val scope: CoroutineScope,
) {
    var draggedKey by mutableStateOf<Any?>(null)
        private set

    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    val offsetAnimatable = Animatable(0f)

    val draggedOffset: Float
        get() = offsetAnimatable.value

    var isDragging by mutableStateOf(false)
        private set

    var justFinishedDrag by mutableStateOf(false)
        private set

    val isReordering: Boolean
        get() = draggedKey != null || isDragging || offsetAnimatable.isRunning || draggedOffset != 0f

    fun isItemDragging(index: Int, key: Any? = null): Boolean {
        val matchesKey = key != null && draggedKey == key
        val matchesIndex = draggedKey == null && draggedIndex == index
        return (matchesKey || matchesIndex) && (isDragging || offsetAnimatable.isRunning || draggedOffset != 0f)
    }

    fun onDragStart(index: Int, key: Any? = null) {
        scope.launch {
            offsetAnimatable.snapTo(0f)
            draggedKey = key
            draggedIndex = index
            isDragging = true
            justFinishedDrag = false
            onDragStartCallback?.invoke()
        }
    }

    fun onDrag(dragAmountY: Float) {
        if (!isDragging) return
        val total = totalItemsCount()
        if (total <= 1) return

        val currentKey = draggedKey
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val currentItem = (if (currentKey != null) visibleItems.firstOrNull { it.key == currentKey } else null)
            ?: visibleItems.firstOrNull { it.index == draggedIndex }
            ?: return
        val currentIndex = currentItem.index

        var newOffset = draggedOffset + dragAmountY

        // Elastic damping at list extremes so item stays visible and feels natural
        if (currentIndex == 0 && newOffset < 0) {
            newOffset = (newOffset * 0.35f).coerceAtLeast(-50f)
        } else if (currentIndex == total - 1 && newOffset > 0) {
            newOffset = (newOffset * 0.35f).coerceAtMost(50f)
        }

        scope.launch {
            offsetAnimatable.snapTo(newOffset)
        }

        // Check neighbor swap using center distance hysteresis
        val draggedCenterY = currentItem.offset + currentItem.size / 2f + newOffset

        if (newOffset > 0 && currentIndex < total - 1) {
            val nextItem = visibleItems.firstOrNull { it.index == currentIndex + 1 }
            if (nextItem != null) {
                val nextCenterY = nextItem.offset + nextItem.size / 2f
                if (draggedCenterY > nextCenterY) {
                    val distance = (nextItem.offset - currentItem.offset).toFloat()
                    onMoveCallback(currentIndex, currentIndex + 1)
                    val adjustedOffset = newOffset - distance
                    scope.launch {
                        offsetAnimatable.snapTo(adjustedOffset)
                    }
                    draggedIndex = currentIndex + 1
                }
            }
        } else if (newOffset < 0 && currentIndex > 0) {
            val prevItem = visibleItems.firstOrNull { it.index == currentIndex - 1 }
            if (prevItem != null) {
                val prevCenterY = prevItem.offset + prevItem.size / 2f
                if (draggedCenterY < prevCenterY) {
                    val distance = (currentItem.offset - prevItem.offset).toFloat()
                    onMoveCallback(currentIndex, currentIndex - 1)
                    val adjustedOffset = newOffset + distance
                    scope.launch {
                        offsetAnimatable.snapTo(adjustedOffset)
                    }
                    draggedIndex = currentIndex - 1
                }
            }
        }
    }

    fun adjustOffsetForScroll(delta: Float) {
        if (!isDragging) return
        val newOffset = draggedOffset + delta
        scope.launch {
            offsetAnimatable.snapTo(newOffset)
        }
    }

    fun onDragEnd() {
        if (!isDragging) return
        isDragging = false
        justFinishedDrag = true
        scope.launch {
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 450f,
                ),
            )
            draggedKey = null
            draggedIndex = null
            onDragEndCallback?.invoke()
            delay(150)
            justFinishedDrag = false
        }
    }

    fun onDragCancel() {
        if (!isDragging) return
        isDragging = false
        justFinishedDrag = true
        scope.launch {
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 450f,
                ),
            )
            draggedKey = null
            draggedIndex = null
            onDragEndCallback?.invoke()
            delay(150)
            justFinishedDrag = false
        }
    }
}

@Composable
fun rememberReorderableState(
    listState: LazyListState,
    totalItemsCount: () -> Int,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
): ReorderableState {
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val scope = rememberCoroutineScope()

    val state = remember(listState) {
        ReorderableState(
            listState = listState,
            totalItemsCount = totalItemsCount,
            onMoveCallback = { from, to -> currentOnMove(from, to) },
            onDragStartCallback = { currentOnDragStart?.invoke() },
            onDragEndCallback = { currentOnDragEnd?.invoke() },
            scope = scope,
        )
    }

    // Auto-scroll when dragging near viewport boundaries
    LaunchedEffect(state.isDragging) {
        if (!state.isDragging) return@LaunchedEffect
        while (state.isDragging) {
            val currentKey = state.draggedKey
            val layoutInfo = state.listState.layoutInfo
            val currentItem = (if (currentKey != null) layoutInfo.visibleItemsInfo.firstOrNull { it.key == currentKey } else null)
                ?: layoutInfo.visibleItemsInfo.firstOrNull { it.index == state.draggedIndex }
            if (currentItem != null) {
                val viewportStart = layoutInfo.viewportStartOffset
                val viewportEnd = layoutInfo.viewportEndOffset
                val currentTop = currentItem.offset + state.draggedOffset
                val currentBottom = currentTop + currentItem.size
                val threshold = 70f
                val maxScrollSpeed = 16f

                val scrollDelta = when {
                    currentTop < viewportStart + threshold && state.listState.canScrollBackward -> {
                        val ratio = ((viewportStart + threshold - currentTop) / threshold).coerceIn(0f, 1f)
                        -ratio * maxScrollSpeed
                    }
                    currentBottom > viewportEnd - threshold && state.listState.canScrollForward -> {
                        val ratio = ((currentBottom - (viewportEnd - threshold)) / threshold).coerceIn(0f, 1f)
                        ratio * maxScrollSpeed
                    }
                    else -> 0f
                }
                if (scrollDelta != 0f) {
                    val consumed = state.listState.scrollBy(scrollDelta)
                    if (consumed != 0f) {
                        state.adjustOffsetForScroll(consumed)
                    }
                }
            }
            delay(16)
        }
    }

    return state
}

/**
 * Standard Compose hold-and-drag gesture recognizer using [awaitLongPressOrCancellation].
 * Triggers cleanly even if the user's finger is held completely still.
 */
suspend fun PointerInputScope.detectHoldAndDrag(
    onDragStart: (Offset) -> Unit,
    onDrag: (dragAmountY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id)
        if (longPress != null) {
            longPress.consume()
            onDragStart(longPress.position)

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!pointer.pressed) {
                        pointer.consume()
                        onDragEnd()
                        break
                    }
                    val deltaY = pointer.position.y - pointer.previousPosition.y
                    pointer.consume()
                    onDrag(deltaY)
                }
            } catch (c: CancellationException) {
                onDragCancel()
                throw c
            }
        }
    }
}

/**
 * Modifier applied to each item in a reorderable list.
 * Listens for long-press to initiate dragging, tracks pointer motion,
 * and smoothly translates the active item vertically with elevation.
 */
fun Modifier.reorderableItem(
    state: ReorderableState,
    key: Any,
    index: Int,
    enabled: Boolean = true,
    haptic: HapticFeedback? = null,
    soundPlayer: LauncherSoundPlayer? = null,
): Modifier = composed {
    val isCurrentItem = state.isItemDragging(index, key)

    val scale by animateFloatAsState(
        targetValue = if (isCurrentItem) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "reorderScale",
    )

    val shadowElevation by animateFloatAsState(
        targetValue = if (isCurrentItem) 20f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "reorderShadow",
    )

    this
        .zIndex(if (isCurrentItem) 3f else 0f)
        .graphicsLayer {
            translationY = if (isCurrentItem) state.draggedOffset else 0f
            scaleX = scale
            scaleY = scale
            this.shadowElevation = shadowElevation
        }
        .then(
            if (enabled) {
                Modifier.pointerInput(key) {
                    detectHoldAndDrag(
                        onDragStart = {
                            haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
                            soundPlayer?.playBobble()
                            state.onDragStart(index, key)
                        },
                        onDrag = { deltaY ->
                            state.onDrag(deltaY)
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
