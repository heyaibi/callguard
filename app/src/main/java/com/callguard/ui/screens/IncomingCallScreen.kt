package com.callguard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callguard.CallGuardApp
import com.callguard.core.PhoneNumbers
import com.callguard.identity.ResolutionResult
import com.callguard.spam.SpamAssessment
import com.callguard.ui.label
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A resolved incoming number and its spam assessment, computed off the main thread. */
private class LiveCaller(
    val result: ResolutionResult,
    val spam: SpamAssessment,
)

/**
 * With [number] null the screen shows the sample caller (tap the name to flip it to spam).
 * With a number it shows the resolved name, source, confidence and spam score; BLOCK and ALLOW write to the lists.
 */
@Composable
fun IncomingCallScreen(
    number: String? = null,
    onDismiss: () -> Unit,
) {
    val graph = CallGuardApp.graph(LocalContext.current)
    val live by produceState<LiveCaller?>(null, number) {
        value =
            number?.let { raw ->
                withContext(Dispatchers.IO) {
                    graph.resolve(raw)?.let { LiveCaller(it, graph.spamPolicy.assess(it.number)) }
                }
            }
    }
    val target = number?.let { PhoneNumbers.normalize(it) }
    var sampleSpam by remember { mutableStateOf(false) }
    var showCard by remember { mutableStateOf(false) }
    val spam = if (number != null) live?.spam?.isSpam == true else sampleSpam
    val glow by animateColorAsState(if (spam) Color(0xFF3A1215) else Color(0xFF3A3A3E))

    val caller = live?.result
    val sourceText =
        when {
            number == null -> "CNAP · HIGH"
            caller == null -> "NO CALLER ID"
            caller.isKnown -> "${caller.source.label()} · ${caller.confidence.name}"
            else -> "NO NAME FOUND"
        }
    val nameLines =
        if (number == null) {
            if (sampleSpam) listOf("LOAN", "DESK") else listOf("PRIYA", "SHARMA")
        } else {
            splitName(caller?.name)
        }
    val numberText =
        if (number == null) {
            if (sampleSpam) "+91 70123 55120" else "+91 98100 44217"
        } else {
            target?.let { PhoneNumbers.format(it) } ?: number
        }
    val pillText = if (spam) "LIKELY SPAM · ${live?.spam?.score ?: 87}" else "is calling…"

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(CgColors.Black)
                .drawBehind {
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colors = listOf(glow, CgColors.Black),
                                center = Offset(size.width / 2f, 0f),
                                radius = size.height * 0.6f,
                            ),
                    )
                }.navigationBarsPadding()
                .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        CallerSourceLabel(text = sourceText, modifier = Modifier.statusBarsPadding())

        AnimatedVisibility(visible = showCard) {
            Column {
                Spacer(Modifier.height(16.dp))
                OverlayCardPreview()
            }
        }

        Spacer(Modifier.weight(1f))

        CallerNameBlock(
            lines = nameLines,
            onClick = if (number == null) ({ sampleSpam = !sampleSpam }) else null,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = numberText,
            style = CgType.Title.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = CgColors.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(CgColors.Red)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = pillText,
                style = CgType.Label.copy(fontSize = 12.sp, letterSpacing = 0.8.sp),
                color = CgColors.White,
            )
        }

        Spacer(Modifier.weight(1f))

        CallAnswerRings(onAnswer = onDismiss)

        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CallActionButton(
                icon = Icons.Filled.Block,
                label = "BLOCK",
                tint = CgColors.Red,
                onClick = {
                    target?.let { graph.settingsStore.block(it) }
                    onDismiss()
                },
            )
            CallActionButton(icon = Icons.Filled.NotificationsOff, label = "SILENCE", tint = CgColors.White, onClick = onDismiss)
            CallActionButton(
                icon = Icons.Filled.Check,
                label = "ALLOW",
                tint = CgColors.White,
                onClick = {
                    target?.let { graph.settingsStore.allow(it) }
                    onDismiss()
                },
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = if (showCard) "HIDE BRIDGE CARD" else "SHOW BRIDGE CARD",
            style = CgType.Label.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            color = CgColors.TextDim,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, CgColors.TextFaint, RoundedCornerShape(50))
                    .clickable { showCard = !showCard }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** Two dot-matrix lines for the caller's name; a missing name reads UNKNOWN CALLER. */
private fun splitName(name: String?): List<String> {
    if (name.isNullOrBlank()) return listOf("UNKNOWN", "CALLER")
    val words = name.trim().uppercase().split(' ', limit = 2)
    return listOf(words[0], words.getOrElse(1) { "" })
}

@Composable
private fun CallerSourceLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
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
        )
    }
}

@Composable
private fun CallerNameBlock(
    lines: List<String>,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier =
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = CgType.DisplayXL.copy(fontSize = 52.sp, lineHeight = 54.sp),
                color = CgColors.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun CallAnswerRings(onAnswer: () -> Unit) {
    Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
        CallRingDisc(diameter = 232.dp, fill = Color(0xFF0C0C0E))
        CallRingDisc(diameter = 176.dp, fill = Color(0xFF131315))
        CallRingDisc(diameter = 120.dp, fill = Color(0xFF1A1A1D))
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(CgColors.White)
                .clickable(onClick = onAnswer),
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
}

@Composable
private fun CallRingDisc(
    diameter: Dp,
    fill: Color,
) {
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, CgColors.Divider, CircleShape),
    )
}

@Composable
private fun CallActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(CgColors.Surface)
                .border(1.dp, CgColors.Divider, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = CgType.Label.copy(fontSize = 10.sp, letterSpacing = 1.2.sp),
            color = CgColors.TextDim,
        )
    }
}

/** Bridge card shown over WhatsApp calls: pill avatar, dot-matrix name, source chip. */
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun OverlayCardPreview() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CgColors.Surface)
            .border(1.dp, CgColors.Divider, RoundedCornerShape(24.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 34.dp)
                .clip(RoundedCornerShape(50))
                .background(CgColors.SurfaceHigh)
                .border(1.5.dp, CgColors.White, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "PS",
                style = CgType.Title.copy(fontSize = 13.sp, lineHeight = 14.sp),
                color = CgColors.White,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "PRIYA SHARMA",
                style = CgType.Title.copy(fontSize = 18.sp, lineHeight = 22.sp),
                color = CgColors.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "WHATSAPP CALL",
                style = CgType.RowSub.copy(fontSize = 11.sp, letterSpacing = 1.5.sp),
                color = CgColors.TextDim,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(CgColors.SurfaceHigh)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "CNAP",
                style = CgType.Label.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
                color = CgColors.White,
            )
        }
    }
}
