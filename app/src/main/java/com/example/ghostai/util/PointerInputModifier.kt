package com.example.ghostai.util

import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.pointerMotionEvents(
    onDown: (PointerInputChange) -> Unit = {},
    onMove: (PointerInputChange) -> Unit = {},
    onUp: (PointerInputChange) -> Unit = {},
    delayAfterDownInMillis: Long = 0L,
    requireUnconsumed: Boolean = true,
    pass: PointerEventPass = PointerEventPass.Main,
    key1: Any? = Unit,
) = this.then(
    Modifier.pointerInput(key1) {
        detectMotionEvents(
            onDown,
            onMove,
            onUp,
            delayAfterDownInMillis,
            requireUnconsumed,
            pass,
        )
    },
)

fun Modifier.pointerTapEvents(
    onDoubleTap: (Offset) -> Unit = {},
    onLongPress: (Offset) -> Unit = {},
    onPress: PressGestureScope.(Offset) -> Unit = {},
    onTap: (Offset) -> Unit = {},
    key1: Any? = Unit,
) = this.then(
    Modifier.pointerInput(key1) {
        detectTapGestures(
            onDoubleTap = onDoubleTap,
            onLongPress = onLongPress,
            onPress = onPress,
            onTap = onTap,
        )
    },
)
