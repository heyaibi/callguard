package com.callguard.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callguard.ui.components.CgLabel
import com.callguard.ui.components.CgPill
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType

/**
 * The dialer that DIAL, VIEW and CALL intents open, and that "Add call" opens during a call. [number] is what has
 * been typed so far. [message] explains a refused call or a missing permission. [isDefaultDialer] false shows the
 * prompt that makes CallGuard the default phone app.
 */
@Composable
fun DialScreen(
    number: String,
    isDefaultDialer: Boolean,
    message: String?,
    onNumberChange: (String) -> Unit,
    onCall: (String) -> Unit,
    onSetDefault: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CgColors.Black)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        if (!isDefaultDialer) {
            CgPill(text = "Set as default phone", containerColor = CgColors.Red, onClick = onSetDefault)
            Spacer(Modifier.height(12.dp))
        }
        CgLabel(text = "New call")
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(48.dp))
            Text(
                text = number.ifEmpty { "ENTER NUMBER" },
                style = CgType.Display.copy(fontSize = 32.sp, lineHeight = 36.sp),
                color = if (number.isEmpty()) CgColors.TextFaint else CgColors.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .alpha(if (number.isEmpty()) 0.35f else 1f)
                    .clip(CircleShape)
                    .clickable(enabled = number.isNotEmpty(), role = Role.Button) {
                        onNumberChange(number.dropLast(1))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Delete digit",
                    tint = CgColors.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = CgType.RowSub,
                color = CgColors.TextDim,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        DialPad(
            onPress = { onNumberChange(number + it) },
            modifier = Modifier.fillMaxWidth(),
            keySize = 68.dp,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(84.dp)
                .alpha(if (number.isEmpty()) 0.35f else 1f)
                .clip(CircleShape)
                .background(CgColors.White)
                .clickable(enabled = number.isNotEmpty(), role = Role.Button) { onCall(number) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Call,
                contentDescription = "Call",
                tint = CgColors.Black,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}
