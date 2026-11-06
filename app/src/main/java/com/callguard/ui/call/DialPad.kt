package com.callguard.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType

private val KEY_ROWS = listOf("123", "456", "789", "*0#")

/**
 * The 3 by 4 keypad. [onPress] runs when a key goes down and [onRelease] when it comes up, so the in-call keypad can
 * hold a DTMF tone while a key is pressed. The dial screen uses [onPress] alone.
 */
@Composable
internal fun DialPad(
    onPress: (Char) -> Unit,
    modifier: Modifier = Modifier,
    keySize: Dp = 68.dp,
    onRelease: () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KEY_ROWS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit -> DialKey(digit, keySize, onPress, onRelease) }
            }
        }
    }
}

@Composable
private fun DialKey(digit: Char, diameter: Dp, onPress: (Char) -> Unit, onRelease: () -> Unit) {
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(CgColors.Surface)
            .pointerInput(digit) {
                detectTapGestures(
                    onPress = {
                        onPress(digit)
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = digit.toString(),
            style = CgType.Title.copy(fontSize = 26.sp, lineHeight = 28.sp),
            color = CgColors.White,
        )
    }
}
