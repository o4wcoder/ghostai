package com.fourthwardai.ghostai.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventPass.Main
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

suspend fun PointerInputScope.detectMotionEvents(
    onDown: (PointerInputChange) -> Unit = {},
    onMove: (PointerInputChange) -> Unit = {},
    onUp: (PointerInputChange) -> Unit = {},
    delayAfterDownInMillis: Long = 0L,
    requireUnconsumed: Boolean = true,
    pass: PointerEventPass = Main,
) {
    coroutineScope {
        awaitEachGesture {
            val down: PointerInputChange =
                awaitFirstDown(
                    requireUnconsumed = requireUnconsumed,
                    pass = pass,
                )
            onDown(down)

            var pointer = down
            var pointerId = down.id
            var waitedAfterDown = false

            launch {
                delay(delayAfterDownInMillis)
                waitedAfterDown = true
            }

            while (true) {
                val event: PointerEvent = awaitPointerEvent(pass)
                val anyPressed = event.changes.any { it.pressed }

                if (anyPressed) {
                    val pointerInputChange =
                        event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.first()

                    pointerId = pointerInputChange.id
                    pointer = pointerInputChange

                    if (waitedAfterDown) {
                        onMove(pointer)
                    }
                } else {
                    onUp(pointer)
                    break
                }
            }
        }
    }
}

suspend fun PointerInputScope.detectMotionEventsAsList(
    onDown: (PointerInputChange) -> Unit = {},
    onMove: (List<PointerInputChange>) -> Unit = {},
    onUp: (PointerInputChange) -> Unit = {},
    delayAfterDownInMillis: Long = 0L,
    requireUnconsumed: Boolean = true,
    pass: PointerEventPass = Main,
) {
    coroutineScope {
        awaitEachGesture {
            val down: PointerInputChange =
                awaitFirstDown(
                    requireUnconsumed = requireUnconsumed,
                    pass = pass,
                )
            onDown(down)

            var pointer = down
            var pointerId = down.id
            var waitedAfterDown = false

            launch {
                delay(delayAfterDownInMillis)
                waitedAfterDown = true
            }

            while (true) {
                val event: PointerEvent = awaitPointerEvent(pass)
                val anyPressed = event.changes.any { it.pressed }

                if (anyPressed) {
                    val pointerInputChange =
                        event.changes.firstOrNull { it.id == pointerId }
                            ?: event.changes.first()

                    pointerId = pointerInputChange.id
                    pointer = pointerInputChange

                    if (waitedAfterDown) {
                        onMove(event.changes)
                    }
                } else {
                    onUp(pointer)
                    break
                }
            }
        }
    }
}
