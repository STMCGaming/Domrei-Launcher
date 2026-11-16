package com.domrei.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
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
    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    val offsetAnimatable = Animatable(0f)

    val draggedOffset: Float
        get() = offsetAnimatable.value

    var isDragging by mutableStateOf(false)
        private set

    var justFinishedDrag by mutableStateOf(false)
        private set

    fun onDragStart(index: Int) {
        scope.launch {
            offsetAnimatable.snapTo(0f)
            draggedIndex = index
            isDragging = true
            justFinishedDrag = false
            onDragStartCallback?.invoke()
        }
    }

    fun onDrag(dragAmountY: Float) {
        if (!isDragging) return
        val currentIndex = draggedIndex ?: return
        val total = totalItemsCount()

        var newOffset = draggedOffset + dragAmountY

        // Clamp offset at top and bottom of the list so it cannot fly off screen
        if (currentIndex == 0 && newOffset < -70f) {
            newOffset = -70f
        } else if (currentIndex == total - 1 && newOffset > 70f) {
            newOffset = 70f
        }

        scope.launch {
            offsetAnimatable.snapTo(newOffset)
        }

        checkSwap(currentIndex, newOffset)
    }

    fun adjustOffsetForScroll(delta: Float) {
        if (!isDragging) return
        val currentIndex = draggedIndex ?: return
        val newOffset = draggedOffset + delta
        scope.launch {
            offsetAnimatable.snapTo(newOffset)
        }
        checkSwap(currentIndex, newOffset)
    }

    private fun checkSwap(currentIndex: Int, currentOffset: Float) {
        val total = totalItemsCount()
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.firstOrNull { it.index == currentIndex } ?: return

        if (currentOffset > 0 && currentIndex < total - 1) {
            // Dragging down: check neighbor below
            val nextItem = visibleItems.firstOrNull { it.index == currentIndex + 1 }
            val distance = if (nextItem != null) {
                (nextItem.offset - currentItem.offset).toFloat()
            } else {
                currentItem.size.toFloat() + 8f
            }
            val threshold = distance * 0.5f

            if (currentOffset > threshold) {
                val targetIndex = currentIndex + 1
                onMoveCallback(currentIndex, targetIndex)
                val newOffset = currentOffset - distance
                scope.launch {
                    offsetAnimatable.snapTo(newOffset)
                }
                draggedIndex = targetIndex
            }
        } else if (currentOffset < 0 && currentIndex > 0) {
            // Dragging up: check neighbor above
            val prevItem = visibleItems.firstOrNull { it.index == currentIndex - 1 }
            val distance = if (prevItem != null) {
                (currentItem.offset - prevItem.offset).toFloat()
            } else {
                currentItem.size.toFloat() + 8f
            }
            val threshold = distance * 0.5f

            if (currentOffset < -threshold) {
                val targetIndex = currentIndex - 1
                onMoveCallback(currentIndex, targetIndex)
                val newOffset = currentOffset + distance
                scope.launch {
                    offsetAnimatable.snapTo(newOffset)
                }
                draggedIndex = targetIndex
            }
        }
    }

    fun onDragEnd() {
        if (!isDragging) return
        isDragging = false
        justFinishedDrag = true
        scope.launch {
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 550f),
            )
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
            offsetAnimatable.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 550f))
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
            val currentIndex = state.draggedIndex ?: break
            val layoutInfo = state.listState.layoutInfo
            val currentItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
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
 * Custom hold-and-drag gesture recognizer with natural finger wobble tolerance
 * and responsive 320ms hold threshold.
 */
suspend fun PointerInputScope.detectHoldAndDrag(
    onDragStart: (Offset) -> Unit,
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val timeout = 320L
    val slopPx = 18.dp.toPx()

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var isHeld = false
        val downPos = down.position
        val downTime = System.currentTimeMillis()

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val currentPointer = event.changes.firstOrNull { it.id == down.id } ?: break

            if (!currentPointer.pressed) {
                break
            }

            val elapsed = System.currentTimeMillis() - downTime
            val movedDistance = (currentPointer.position - downPos).getDistance()

            if (movedDistance > slopPx && elapsed < timeout) {
                // User is scrolling / swiping, cancel hold
                break
            }

            if (elapsed >= timeout) {
                isHeld = true
                currentPointer.consume()
                break
            }
        }

        if (isHeld) {
            onDragStart(downPos)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val currentPointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!currentPointer.pressed) {
                        currentPointer.consume()
                        onDragEnd()
                        break
                    }
                    val previousPos = currentPointer.previousPosition
                    val currentPos = currentPointer.position
                    val dragDelta = currentPos - previousPos
                    currentPointer.consume()
                    onDrag(currentPointer, dragDelta)
                }
            } catch (e: CancellationException) {
                onDragCancel()
                throw e
            }
        }
    }
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
                    detectHoldAndDrag(
                        onDragStart = {
                            haptic?.performHapticFeedback(HapticFeedbackType.LongPress)
                            soundPlayer?.playBobble()
                            state.onDragStart(index)
                        },
                        onDrag = { change, dragAmount ->
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
