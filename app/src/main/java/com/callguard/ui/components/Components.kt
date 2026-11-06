package com.callguard.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import com.callguard.ui.theme.DotFont
import com.callguard.ui.theme.MonoFont

/** Horizontal gutter every screen uses for headings, cards and rows. */
val CgGutter: Dp = 20.dp

private val PillText = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)

/** Drops the colour from contact photos, so they sit in the monochrome palette. */
private val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Pill-shaped avatar with a white outline. Shows [photo] when there is one, otherwise the initials.
 */
@Composable
fun PillAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    ringColor: Color = CgColors.White,
    width: Dp = 46.dp,
    height: Dp = 30.dp,
    photo: ImageBitmap? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(CgColors.SurfaceHigh)
            .border(1.5.dp, ringColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = Greyscale,
                modifier = Modifier.fillMaxSize().padding(2.dp).clip(shape),
            )
        } else {
            Text(
                text = initials.uppercase(),
                style = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp),
                color = ringColor,
                maxLines = 1,
            )
        }
    }
}

/** Dot-matrix uppercase heading. */
@Composable
fun DotHeading(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = CgType.Display,
    color: Color = CgColors.White,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Small monospace caps label used for section names and field captions. */
@Composable
fun CgLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CgColors.TextDim,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = CgType.Label,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Dotted section letter, used as a list group header. */
@Composable
fun SectionLetter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = CgGutter, top = 20.dp, bottom = 6.dp),
        style = TextStyle(fontFamily = DotFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 18.sp, letterSpacing = 2.sp),
        color = CgColors.TextDim,
    )
}

/** Thin full-width divider. */
@Composable
fun CgDivider(modifier: Modifier = Modifier, startInset: Dp = 0.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startInset)
            .height(1.dp)
            .background(CgColors.Divider),
    )
}

/**
 * Monospace caps row: optional leading slot (avatar), NAME in caps with wide tracking,
 * a dimmer subtitle (number, time), and an optional trailing slot (tag, chip, button).
 * [onLongClick] is optional; [titleColor] lets a row flag itself, such as a missed call in red.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MonoRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    titleColor: Color = CgColors.White,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null || onLongClick != null) {
                        Modifier.combinedClickable(role = Role.Button, onClick = onClick ?: {}, onLongClick = onLongClick)
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = CgGutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = CgType.Row,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle.uppercase(),
                        style = CgType.RowSub,
                        color = CgColors.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (divider) CgDivider()
    }
}

/** Round graphite key (dialer, sheet buttons). Pass [containerColor] for white or red variants. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoundKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    containerColor: Color = CgColors.Surface,
    contentColor: Color = CgColors.White,
    outlined: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (outlined) Color.Transparent else containerColor)
            .then(if (outlined) Modifier.border(1.dp, CgColors.TextFaint, CircleShape) else Modifier)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/** Generic pill tag or button. */
@Composable
fun CgPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = CgColors.SurfaceHigh,
    contentColor: Color = CgColors.White,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text.uppercase(), style = PillText, color = contentColor, maxLines = 1)
    }
}

/**
 * Small red pill. [filled] gives a solid red tag (primary or destructive).
 * [dashed] with [filled] false gives the red dashed outline used for "+ Add contact".
 */
@Composable
fun RedPill(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    dashed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    when {
        filled -> CgPill(text, modifier, containerColor = CgColors.Red, contentColor = CgColors.White, onClick = onClick)
        dashed -> CgPill(text, modifier.dashedRedOutline(), containerColor = Color.Transparent, contentColor = CgColors.Red, onClick = onClick)
        else -> CgPill(text, modifier, containerColor = Color.Transparent, contentColor = CgColors.Red, borderColor = CgColors.Red, onClick = onClick)
    }
}

private fun Modifier.dashedRedOutline(): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = CgColors.Red,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(size.height / 2f),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))),
    )
}

/** Call outcome tag. Expects ALLOWED, SILENCED or BLOCKED (any case). */
@Composable
fun OutcomeTag(outcome: String, modifier: Modifier = Modifier) {
    when (outcome.uppercase()) {
        "BLOCKED" -> RedPill(outcome, modifier)
        "SILENCED" -> CgPill(outcome, modifier, containerColor = Color.Transparent, contentColor = CgColors.TextDim, borderColor = CgColors.TextFaint)
        else -> CgPill(outcome, modifier, containerColor = CgColors.SurfaceHigh, contentColor = CgColors.White)
    }
}

/** Where a name came from: source in bold, confidence dimmed. */
@Composable
fun SourceChip(source: String, confidence: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, CgColors.TextFaint, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = source.uppercase(),
            style = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.sp),
            color = CgColors.White,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = confidence.uppercase(),
            style = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 9.sp, letterSpacing = 0.5.sp),
            color = CgColors.TextDim,
            maxLines = 1,
        )
    }
}

/** Thin spam meter. Turns red at or above [threshold]. */
@Composable
fun ScoreBar(score: Int, modifier: Modifier = Modifier, threshold: Int = 70) {
    val fraction = score.coerceIn(0, 100) / 100f
    val fill = if (score >= threshold) CgColors.Red else CgColors.White
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(CgColors.SurfaceHigh),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(fill),
        )
    }
}

/** Dark pill switch: white track when on. */
@Composable
fun CgToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = CgColors.Black,
            checkedTrackColor = CgColors.White,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = CgColors.TextDim,
            uncheckedTrackColor = CgColors.Surface,
            uncheckedBorderColor = CgColors.TextFaint,
        ),
    )
}

/** One pill in an [ActionSheet]: its label, icon and tap action. A disabled action is drawn dim and takes no taps. */
data class SheetAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Bottom-sheet content for one call or contact: a header, [actions] in a two-column grid (an odd last one spans the
 * row), then a bottom row of delete (red, only when [onDelete] is set), share (white) and edit (outlined).
 * Place it inside [SheetOverlay] at the call site.
 */
@Composable
fun ActionSheet(
    name: String?,
    number: String,
    initials: String,
    actions: List<SheetAction>,
    modifier: Modifier = Modifier,
    photo: ImageBitmap? = null,
    onDelete: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = CgGutter, end = CgGutter, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillAvatar(initials, photo = photo)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = (name ?: "Unknown number").uppercase(),
                    style = CgType.Row,
                    color = CgColors.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = number,
                    style = CgType.RowSub,
                    color = CgColors.TextDim,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        actions.chunked(2).forEachIndexed { index, pair ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { SheetButton(it, Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDelete != null) {
                RoundKey(onClick = onDelete, size = 68.dp, containerColor = CgColors.Red) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            } else {
                Spacer(Modifier.size(68.dp))
            }
            RoundKey(onClick = onShare, size = 68.dp, containerColor = CgColors.White, contentColor = CgColors.Black) {
                Icon(Icons.Filled.Share, contentDescription = "Share")
            }
            RoundKey(onClick = onEdit, size = 68.dp, outlined = true) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
        }
    }
}

@Composable
private fun SheetButton(action: SheetAction, modifier: Modifier = Modifier) {
    val tint = if (action.enabled) CgColors.White else CgColors.TextFaint
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(CgColors.SurfaceHigh)
            .then(if (action.enabled) Modifier.clickable(role = Role.Button, onClick = action.onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = action.label.uppercase(),
            style = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * A bottom sheet drawn over the screen it is placed in: a dim scrim (tap it to close) and a panel that slides up
 * above the tab bar. It is an in-place overlay rather than a ModalBottomSheet, because a dialog window would draw
 * a light nav bar. The content stays where it is while the panel slides out.
 */
@Composable
fun BoxScope.SheetOverlay(
    open: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = open,
        modifier = Modifier.matchParentSize(),
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
    }
    AnimatedVisibility(
        visible = open,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(CgColors.Surface)
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(CgColors.TextFaint),
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** Two-button confirm over the screen: a cancel text and a red pill that runs [onConfirm]. */
@Composable
fun CgConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(28.dp))
                .background(CgColors.Surface)
                .padding(22.dp),
        ) {
            Text(title, style = CgType.Title, color = CgColors.White)
            Spacer(Modifier.height(12.dp))
            Text(body, style = CgType.RowSub.copy(fontSize = 10.sp, letterSpacing = 0.8.sp), color = CgColors.TextDim)
            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CANCEL",
                    style = CgType.Label,
                    color = CgColors.TextDim,
                    modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(CgColors.Red)
                        .clickable(onClick = onConfirm)
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(text = confirmLabel, style = CgType.Label, color = CgColors.White)
                }
            }
        }
    }
}
