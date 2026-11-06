package com.callguard.ui.call

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callguard.call.AudioRoute
import com.callguard.call.CallPhase
import com.callguard.call.CallState
import com.callguard.call.formatElapsed
import com.callguard.ui.components.CgPill
import com.callguard.ui.components.PillAvatar
import com.callguard.ui.initialsOf
import com.callguard.ui.label
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import kotlinx.coroutines.delay

/** Every action the in-call screens can take. The in-call activity connects these to CallManager. */
interface InCallActions {
    fun answer(id: Long)
    fun decline(id: Long)
    fun hangUp(id: Long)
    fun hold(id: Long, hold: Boolean)
    fun setMuted(muted: Boolean)
    fun setSpeaker(on: Boolean)
    fun playDtmf(id: Long, digit: Char)
    fun stopDtmf(id: Long)
    fun addCall()
}

/**
 * The in-call UI. A ringing call gets the incoming screen with Answer and Decline. Otherwise the main call gets the
 * call screen with its controls and the hang-up button. Nothing is drawn when no call is left.
 */
@Composable
fun InCallScreen(calls: List<CallState>, actions: InCallActions) {
    val ringing = calls.firstOrNull { it.phase == CallPhase.RINGING }
    val primary = ringing
        ?: calls.firstOrNull { it.phase == CallPhase.ACTIVE }
        ?: calls.firstOrNull { it.phase == CallPhase.HOLDING || it.phase == CallPhase.DIALING }
        ?: calls.lastOrNull()
    when {
        ringing != null -> RingingContent(ringing, actions)
        primary != null -> ActiveContent(primary, actions)
    }
}

@Composable
private fun RingingContent(call: CallState, actions: InCallActions) {
    val glow by animateColorAsState(if (call.spam) Color(0xFF3A1215) else Color(0xFF3A3A3E))
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CgColors.Black)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(glow, CgColors.Black),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.height * 0.6f,
                    ),
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        SourceLine(text = sourceText(call))
        Spacer(Modifier.weight(1f))
        NameLines(lines = nameLines(call))
        Spacer(Modifier.height(14.dp))
        Text(
            text = call.displayNumber,
            style = CgType.Title.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = CgColors.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        StatusPill(
            text = ringingPill(call),
            container = if (call.silenced && !call.spam) CgColors.SurfaceHigh else CgColors.Red,
        )
        Spacer(Modifier.weight(1f))
        AnswerRings(onAnswer = { actions.answer(call.id) })
        Spacer(Modifier.height(20.dp))
        DeclineButton(onDecline = { actions.decline(call.id) })
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun ActiveContent(call: CallState, actions: InCallActions) {
    var keypadShown by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CgColors.Black)
            .systemBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        SourceLine(text = sourceText(call))
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillAvatar(initials = initialsOf(call.name), width = 64.dp, height = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = (call.name ?: call.displayNumber).uppercase(),
                    style = CgType.Title.copy(fontSize = 24.sp, lineHeight = 28.sp),
                    color = CgColors.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                CallStatus(call)
            }
        }
        Spacer(Modifier.weight(1f))
        if (keypadShown) {
            DialPad(
                onPress = { actions.playDtmf(call.id, it) },
                onRelease = { actions.stopDtmf(call.id) },
                modifier = Modifier.fillMaxWidth(),
                keySize = 68.dp,
            )
            Spacer(Modifier.height(18.dp))
            CgPill(text = "Hide keypad", onClick = { keypadShown = false })
        } else {
            CallControls(call, actions, onKeypad = { keypadShown = true })
        }
        Spacer(Modifier.height(28.dp))
        EndCallButton(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = { actions.hangUp(call.id) })
        Spacer(Modifier.height(24.dp))
    }
}

/** The timer while connected; otherwise the call's state in words. */
@Composable
private fun CallStatus(call: CallState) {
    val connectTime = call.connectTime
    if (call.phase == CallPhase.ACTIVE && connectTime != null) {
        val now by produceState(initialValue = System.currentTimeMillis(), key1 = connectTime) {
            while (true) {
                value = System.currentTimeMillis()
                delay(1_000L)
            }
        }
        StatusPill(text = formatElapsed(now - connectTime), container = CgColors.Surface)
    } else {
        val text = when (call.phase) {
            CallPhase.RINGING -> "INCOMING"
            CallPhase.DIALING -> "CALLING…"
            CallPhase.ACTIVE -> "CONNECTED"
            CallPhase.HOLDING -> "ON HOLD"
            CallPhase.DISCONNECTED -> "ENDED"
        }
        StatusPill(text = text, container = CgColors.Surface)
    }
}

@Composable
private fun CallControls(call: CallState, actions: InCallActions, onKeypad: () -> Unit) {
    val onHold = call.phase == CallPhase.HOLDING
    val holdable = call.canHold && (call.phase == CallPhase.ACTIVE || call.phase == CallPhase.HOLDING)
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth()) {
            ControlButton(
                icon = Icons.Filled.MicOff,
                label = if (call.muted) "UNMUTE" else "MUTE",
                modifier = Modifier.weight(1f),
                active = call.muted,
                onClick = { actions.setMuted(!call.muted) },
            )
            ControlButton(
                icon = Icons.Filled.Dialpad,
                label = "KEYPAD",
                modifier = Modifier.weight(1f),
                onClick = onKeypad,
            )
            ControlButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = "SPEAKER",
                modifier = Modifier.weight(1f),
                active = call.route == AudioRoute.SPEAKER,
                onClick = { actions.setSpeaker(call.route != AudioRoute.SPEAKER) },
            )
        }
        Row(Modifier.fillMaxWidth()) {
            ControlButton(
                icon = Icons.Filled.Add,
                label = "ADD CALL",
                modifier = Modifier.weight(1f),
                onClick = { actions.addCall() },
            )
            ControlButton(
                icon = if (onHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                label = if (onHold) "RESUME" else "HOLD",
                modifier = Modifier.weight(1f),
                active = onHold,
                enabled = holdable,
                onClick = { actions.hold(call.id, !onHold) },
            )
            // Video calls are not supported, so the key stays visible but cannot be pressed.
            ControlButton(
                icon = Icons.Filled.Videocam,
                label = "VIDEO",
                modifier = Modifier.weight(1f),
                enabled = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.alpha(if (enabled) 1f else 0.35f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(if (active) CgColors.White else CgColors.Surface)
                .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) CgColors.Black else CgColors.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = CgType.Label.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            color = CgColors.TextDim,
            maxLines = 1,
        )
    }
}

@Composable
private fun EndCallButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(CgColors.Red)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CallEnd,
            contentDescription = "End call",
            tint = CgColors.White,
            modifier = Modifier.size(34.dp),
        )
    }
}

@Composable
private fun DeclineButton(onDecline: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CgColors.Red)
                .clickable(role = Role.Button, onClick = onDecline),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = "Decline",
                tint = CgColors.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "DECLINE",
            style = CgType.Label.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            color = CgColors.TextDim,
        )
    }
}

/** Concentric discs around the white answer button, as on the incoming-call design, labelled like the decline button. */
@Composable
private fun AnswerRings(onAnswer: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(216.dp), contentAlignment = Alignment.Center) {
            RingDisc(diameter = 216.dp, fill = Color(0xFF0C0C0E))
            RingDisc(diameter = 164.dp, fill = Color(0xFF131315))
            RingDisc(diameter = 112.dp, fill = Color(0xFF1A1A1D))
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(CgColors.White)
                    .clickable(role = Role.Button, onClick = onAnswer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Answer",
                    tint = CgColors.Black,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "ANSWER",
            style = CgType.Label.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            color = CgColors.TextDim,
        )
    }
}

@Composable
private fun RingDisc(diameter: Dp, fill: Color) {
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, CgColors.Divider, CircleShape),
    )
}

/** Dot-and-text line above the caller: where the name came from and how sure CallGuard is. */
@Composable
private fun SourceLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(9.dp)) {
            drawCircle(
                color = CgColors.White,
                radius = size.minDimension / 2f - 0.5.dp.toPx(),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(color = CgColors.White, radius = 1.5.dp.toPx())
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = CgType.RowSub.copy(fontSize = 11.sp, letterSpacing = 1.5.sp),
            color = CgColors.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NameLines(lines: List<String>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        lines.forEach { line ->
            Text(
                text = line,
                style = CgType.DisplayXL.copy(fontSize = 44.sp, lineHeight = 46.sp),
                color = CgColors.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, container: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = CgType.Label.copy(fontSize = 12.sp, letterSpacing = 0.8.sp),
            color = CgColors.White,
            maxLines = 1,
        )
    }
}

private fun sourceText(call: CallState): String = when {
    call.hidden -> "HIDDEN NUMBER"
    call.name != null -> "${call.source.label()} · ${call.confidence.name}"
    else -> "NO NAME FOUND"
}

private fun ringingPill(call: CallState): String = when {
    call.spam -> "LIKELY SPAM · ${call.spamScore}"
    call.silenced -> "SILENCED · is calling…"
    else -> "is calling…"
}

/** Two dot-matrix lines for the caller's name. A missing name reads UNKNOWN CALLER, or PRIVATE NUMBER when hidden. */
private fun nameLines(call: CallState): List<String> {
    val name = call.name
    if (name.isNullOrBlank()) {
        return if (call.hidden) listOf("PRIVATE", "NUMBER") else listOf("UNKNOWN", "CALLER")
    }
    val words = name.trim().uppercase().split(' ', limit = 2)
    return listOf(words[0], words.getOrElse(1) { "" })
}
