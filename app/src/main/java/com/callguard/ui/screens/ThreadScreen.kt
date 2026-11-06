package com.callguard.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ClipboardManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callguard.CallGuardApp
import com.callguard.core.PhoneNumber
import com.callguard.core.PhoneNumbers
import com.callguard.messaging.DeliveryState
import com.callguard.messaging.MessageNotifier
import com.callguard.messaging.SmsProvider
import com.callguard.messaging.ThreadMessage
import com.callguard.messaging.ThreadRow
import com.callguard.messaging.isDefaultSmsApp
import com.callguard.messaging.smsCount
import com.callguard.messaging.SmsCount
import com.callguard.messaging.threadRows
import com.callguard.ui.components.RedPill
import com.callguard.ui.components.RoundKey
import com.callguard.ui.dayLabel
import com.callguard.ui.rememberPlaceCall
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import com.callguard.ui.theme.MonoFont
import com.callguard.ui.timeLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

private val BubbleText = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

/**
 * One conversation with [number]: its texts from the SMS provider, oldest first, with a day heading before each day
 * and a delivery mark on each outgoing text. Opening the thread marks its texts read and clears their notification.
 * The list reloads when the provider changes, so a text that arrives while the screen is open appears.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThreadScreen(number: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val graph = CallGuardApp.graph(context)
    val target = remember(number) { PhoneNumber(number) }
    val scope = rememberCoroutineScope()
    val placeCall = rememberPlaceCall()
    val name by produceState<String?>(initialValue = null, target) {
        value = withContext(Dispatchers.IO) { graph.resolutionPolicy.resolve(target).name }
    }

    var canRead by remember { mutableStateOf(hasSmsPermission(context, Manifest.permission.READ_SMS)) }
    var canSend by remember { mutableStateOf(hasSmsPermission(context, Manifest.permission.SEND_SMS)) }
    var messages by remember { mutableStateOf<List<ThreadMessage>>(emptyList()) }
    var draft by rememberSaveable { mutableStateOf("") }
    var sendFailed by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ThreadMessage?>(null) }

    fun reload() {
        scope.launch {
            messages = withContext(Dispatchers.IO) {
                val loaded = SmsProvider.thread(context, target)
                if (loaded.any { it.isUnreadIncoming } && isDefaultSmsApp(context)) {
                    SmsProvider.markRead(context, target)
                }
                MessageNotifier.cancel(context, target)
                loaded
            }
        }
    }

    LaunchedEffect(canRead, target) {
        if (canRead) reload()
    }

    DisposableEffect(number) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = reload()
        }
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        canRead = hasSmsPermission(context, Manifest.permission.READ_SMS)
        canSend = hasSmsPermission(context, Manifest.permission.SEND_SMS)
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        if (!canSend) {
            permissions.launch(arrayOf(Manifest.permission.SEND_SMS))
            return
        }
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { SmsProvider.send(context, target, text) }.exceptionOrNull()
            }
            sendFailed = error != null
            if (error == null) draft = ""
            reload()
        }
    }

    fun resend(message: ThreadMessage) {
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    SmsProvider.send(context, target, message.body)
                    SmsProvider.deleteMessage(context, message.id)
                }.exceptionOrNull()
            }
            sendFailed = error != null
            reload()
        }
    }

    fun delete(message: ThreadMessage) {
        scope.launch {
            withContext(Dispatchers.IO) { SmsProvider.deleteMessage(context, message.id) }
            reload()
        }
    }

    val rows = remember(messages) { threadRows(messages, ZoneId.systemDefault()) }
    val listState = rememberLazyListState()
    LaunchedEffect(rows.size) {
        if (rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex)
    }
    // The list shrinks when the keyboard opens; keep the newest text in view.
    val imeOpen = WindowInsets.isImeVisible
    LaunchedEffect(imeOpen) {
        // Wait for the keyboard to finish sliding up, so the scroll lands on the final viewport.
        if (imeOpen) {
            delay(400)
            if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
        }
    }

    val numberLabel = PhoneNumbers.format(target)
    Column(Modifier.fillMaxSize().background(CgColors.Black)) {
        ThreadHeader(
            title = name ?: numberLabel,
            subtitle = if (name != null) numberLabel else null,
            onBack = onBack,
            onCall = { placeCall(target.e164) },
            onAllow = {
                scope.launch {
                    withContext(Dispatchers.IO) { graph.settingsStore.allow(target) }
                    context.toast("Sender allowed")
                }
            },
            onBlock = {
                scope.launch {
                    withContext(Dispatchers.IO) { graph.settingsStore.block(target) }
                    context.toast("Sender blocked")
                }
            },
        )
        if (canRead) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(
                    rows,
                    key = {
                        when (it) {
                            is ThreadRow.Day -> "day-${it.millis}"
                            is ThreadRow.Text -> "msg-${it.message.id}"
                        }
                    },
                ) { row ->
                    when (row) {
                        is ThreadRow.Day -> DayHeading(row.millis)
                        is ThreadRow.Text -> Bubble(
                            message = row.message,
                            menuOpen = menuFor?.id == row.message.id,
                            onLongPress = { menuFor = row.message },
                            onDismiss = { menuFor = null },
                            onCopy = {
                                menuFor = null
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("text message", row.message.body))
                            },
                            onDelete = {
                                menuFor = null
                                delete(row.message)
                            },
                            onResend = {
                                menuFor = null
                                resend(row.message)
                            },
                        )
                    }
                }
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "READ SMS PERMISSION NEEDED", style = CgType.Label, color = CgColors.TextDim)
                Spacer(Modifier.height(14.dp))
                RedPill(
                    text = "GRANT READ SMS",
                    onClick = { permissions.launch(arrayOf(Manifest.permission.READ_SMS)) },
                )
            }
        }
        if (sendFailed) {
            Text(
                text = "SEND FAILED · CHECK SMS PERMISSION",
                style = CgType.Label.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
                color = CgColors.Red,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        ComposeBar(
            draft = draft,
            onDraftChange = { draft = it },
            onSend = { send() },
        )
    }
}

@Composable
private fun ThreadHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onAllow: () -> Unit,
    onBlock: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CgColors.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                style = CgType.Row,
                color = CgColors.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = CgType.RowSub,
                    color = CgColors.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onCall) {
            Icon(Icons.Filled.Call, contentDescription = "Call", tint = CgColors.White)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = CgColors.White)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = CgColors.SurfaceHigh,
            ) {
                ThreadMenuItem("Allow sender", CgColors.White) {
                    menuOpen = false
                    onAllow()
                }
                ThreadMenuItem("Block sender", CgColors.Red) {
                    menuOpen = false
                    onBlock()
                }
            }
        }
    }
}

@Composable
private fun DayHeading(millis: Long) {
    Text(
        text = dayLabel(millis),
        style = CgType.Label.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
        color = CgColors.TextFaint,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun Bubble(
    message: ThreadMessage,
    menuOpen: Boolean,
    onLongPress: () -> Unit,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onResend: () -> Unit,
) {
    val state = message.state
    val fromMe = state != DeliveryState.RECEIVED
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = if (fromMe) Alignment.End else Alignment.Start,
    ) {
        Box {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bubbleColor(state))
                    .pointerInput(message.id) { detectTapGestures(onLongPress = { onLongPress() }) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = message.body,
                    style = BubbleText,
                    color = if (state == DeliveryState.SENT || state == DeliveryState.PENDING) CgColors.Black else CgColors.White,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = onDismiss,
                containerColor = CgColors.SurfaceHigh,
            ) {
                ThreadMenuItem("Copy", CgColors.White, onCopy)
                if (state == DeliveryState.FAILED) ThreadMenuItem("Resend", CgColors.White, onResend)
                ThreadMenuItem("Delete", CgColors.Red, onDelete)
            }
        }
        StatusLine(message)
    }
}

/** Time under a bubble, with the delivery mark for outgoing texts. */
@Composable
private fun StatusLine(message: ThreadMessage) {
    Row(
        Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeLabel(message.timestamp),
            style = CgType.RowSub.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
            color = CgColors.TextFaint,
        )
        val (icon, label, tint) = when (message.state) {
            DeliveryState.RECEIVED -> return
            DeliveryState.PENDING -> Triple(Icons.Filled.Schedule, "SENDING", CgColors.TextDim)
            DeliveryState.SENT -> Triple(Icons.Filled.Done, "SENT", CgColors.TextDim)
            DeliveryState.FAILED -> Triple(Icons.Filled.ErrorOutline, "FAILED", CgColors.Red)
        }
        Spacer(Modifier.width(6.dp))
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            style = CgType.RowSub.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
            color = tint,
        )
    }
}

@Composable
private fun ComposeBar(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    val count = remember(draft) { smsCount(draft) }
    // The window does not resize for the keyboard (edge-to-edge, adjustNothing in the manifest), so this bar lifts itself
    // above the keyboard. It is the only place the IME inset is applied.
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        if (draft.isNotEmpty()) {
            Text(
                text = counterLabel(count),
                style = CgType.Label.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                color = if (count.parts > 1) CgColors.Amber else CgColors.TextFaint,
                modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 8.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(CgColors.Surface)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = BubbleText.copy(color = CgColors.White),
                    cursorBrush = SolidColor(CgColors.White),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(text = "MESSAGE", style = CgType.Label, color = CgColors.TextFaint)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            RoundKey(
                onClick = onSend,
                size = 48.dp,
                containerColor = CgColors.White,
                contentColor = CgColors.Black,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

private fun counterLabel(count: SmsCount): String {
    val parts = if (count.parts == 1) "PART" else "PARTS"
    val script = if (count.unicode) " · UNICODE" else ""
    return "${count.remaining} LEFT · ${count.parts} $parts$script"
}

private fun bubbleColor(state: DeliveryState): Color = when (state) {
    DeliveryState.RECEIVED -> CgColors.SurfaceHigh
    DeliveryState.PENDING -> CgColors.White.copy(alpha = 0.55f)
    DeliveryState.SENT -> CgColors.White
    DeliveryState.FAILED -> CgColors.Red
}

@Composable
private fun ThreadMenuItem(label: String, color: Color, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label.uppercase(), style = CgType.Label.copy(fontSize = 11.sp), color = color) },
        onClick = onClick,
    )
}

private fun hasSmsPermission(context: Context, permission: String): Boolean =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.toast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
