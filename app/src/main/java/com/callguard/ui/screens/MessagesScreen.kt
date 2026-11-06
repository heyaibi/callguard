package com.callguard.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.AppGraph
import com.callguard.CallGuardApp
import com.callguard.core.PhoneNumber
import com.callguard.messaging.Conversation
import com.callguard.messaging.MessageNotifier
import com.callguard.messaging.SmsProvider
import com.callguard.messaging.groupConversations
import com.callguard.messaging.isDefaultSmsApp
import com.callguard.messaging.matchesQuery
import com.callguard.messaging.splitConversations
import com.callguard.ui.components.OutcomeTag
import com.callguard.ui.components.PillAvatar
import com.callguard.ui.components.RedPill
import com.callguard.ui.components.RoundKey
import com.callguard.ui.displayNumber
import com.callguard.ui.initialsOf
import com.callguard.ui.listTimeLabel
import com.callguard.ui.rememberPlaceCall
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import com.callguard.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAB_INBOX = 0
private const val TAB_QUIET = 1

/** A burst of list changes waits this long, then the list reloads once. */
private const val RELOAD_DELAY_MS = 150L

/** One conversation row: the conversation, its contact name, and whether the sender is blocked. */
internal data class ListRow(
    val conversation: Conversation,
    val name: String?,
    val blocked: Boolean,
)

internal data class MessagesModel(
    val inbox: List<ListRow>,
    val quiet: List<ListRow>,
)

/**
 * The messages list: conversations from the SMS provider, one row per sender, plus the quiet inbox of held-back and
 * blocked senders. Long-press a row for its actions. The red key starts a new message. The last list loaded is kept in
 * the graph, so a return here shows it at once while it refreshes.
 */
@Composable
fun MessagesScreen(
    onOpenThread: (String) -> Unit = {},
    onCompose: () -> Unit = {},
) {
    val context = LocalContext.current
    val graph = CallGuardApp.graph(context)
    val scope = rememberCoroutineScope()
    val logs by graph.repos.messages
        .observe()
        .collectAsStateWithLifecycle()
    val settings by graph.settingsStore.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(TAB_INBOX) }
    var query by rememberSaveable { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var sheetRow by remember { mutableStateOf<ListRow?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val placeCall = rememberPlaceCall()
    val smsRole = remember { context.getSystemService(RoleManager::class.java) }
    val smsRoleAvailable = remember { smsRole.isRoleAvailable(RoleManager.ROLE_SMS) }
    var isDefault by remember { mutableStateOf(isDefaultSmsApp(context)) }
    var readGranted by remember { mutableStateOf(hasSmsPermission(context, Manifest.permission.READ_SMS)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefault = isDefaultSmsApp(context)
        readGranted = hasSmsPermission(context, Manifest.permission.READ_SMS)
    }
    val roleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isDefault = isDefaultSmsApp(context)
        }
    val readLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            readGranted = granted
            revision++
        }

    // Texts arriving or leaving change the provider; the list reloads when that happens.
    DisposableEffect(Unit) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    revision++
                }
            }
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    // The last list loaded, kept in the graph. It is null only until the first load lands.
    val cached by graph.messagesModel.collectAsStateWithLifecycle()
    val model = cached ?: MessagesModel(emptyList(), emptyList())
    // A change restarts this effect, so a burst of changes waits out the delay and loads once. The cached list stays on
    // screen until the next load replaces it.
    LaunchedEffect(revision, settings, logs, readGranted) {
        delay(RELOAD_DELAY_MS)
        graph.messagesModel.value = withContext(Dispatchers.IO) { loadModel(context, graph) }
    }
    val rows = if (tab == TAB_INBOX) model.inbox else model.quiet
    val visible =
        rows.filter {
            matchesQuery(query, it.name, it.conversation.number.e164, it.conversation.snippet)
        }
    val unread = model.inbox.sumOf { it.conversation.unread }
    val now = System.currentTimeMillis()

    fun act(block: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { block() }
            revision++
        }
    }

    fun closeSheet() {
        sheetOpen = false
        confirmDelete = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "header") { MsgHeader(unread = unread) }
            item(key = "search") { SearchPill(query = query, onQueryChange = { query = it }) }
            if (smsRoleAvailable && !isDefault) {
                item(key = "default-banner") {
                    SmsBanner(
                        message = "NON-CONTACT TEXTS CAN BE HELD BACK ONLY WHEN CALLGUARD IS THE DEFAULT SMS APP",
                        button = "SET AS DEFAULT SMS APP TO BLOCK TEXTS",
                        onRequest = { roleLauncher.launch(smsRole.createRequestRoleIntent(RoleManager.ROLE_SMS)) },
                    )
                }
            }
            if (!readGranted) {
                item(key = "read-banner") {
                    SmsBanner(
                        message = "GRANT READ SMS TO SEE YOUR TEXT CONVERSATIONS",
                        button = "GRANT READ SMS",
                        onRequest = { readLauncher.launch(Manifest.permission.READ_SMS) },
                    )
                }
            }
            item(key = "tabs") {
                MsgTabs(
                    selected = tab,
                    inboxUnread = unread,
                    quietCount = model.quiet.size,
                    onSelect = {
                        tab = it
                        closeSheet()
                    },
                )
            }
            val emptyText =
                emptyListLabel(
                    loaded = cached != null,
                    visibleRows = visible.size,
                    query = query,
                    quiet = tab == TAB_QUIET,
                )
            if (emptyText != null) {
                item(key = "empty") {
                    Text(
                        text = emptyText,
                        style = CgType.Label,
                        color = CgColors.TextFaint,
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 40.dp),
                    )
                }
            }
            items(visible, key = { it.conversation.number.e164 }) { row ->
                ConversationRowItem(
                    row = row,
                    quiet = tab == TAB_QUIET,
                    now = now,
                    onOpen = { onOpenThread(row.conversation.number.e164) },
                    onLongPress = {
                        sheetRow = row
                        confirmDelete = false
                        sheetOpen = true
                    },
                    onMoveToInbox = { act { graph.settingsStore.allow(row.conversation.number) } },
                )
                MsgDivider(indent = true)
            }
        }

        // Scrim: tap outside the sheet to close it.
        AnimatedVisibility(
            visible = sheetOpen,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = ::closeSheet,
                        ),
            )
        }

        // Sheet: slides up above the tab bar. Its content stays while it animates out.
        AnimatedVisibility(
            visible = sheetOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(CgColors.Surface)
                        .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 32.dp, height = 4.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(CgColors.TextFaint),
                )
                Spacer(Modifier.height(16.dp))
                sheetRow?.let { row ->
                    val number = row.conversation.number
                    ConversationSheet(
                        row = row,
                        quiet = tab == TAB_QUIET,
                        confirming = confirmDelete,
                        onCall = {
                            placeCall(number.e164)
                            closeSheet()
                        },
                        onMarkRead = {
                            act {
                                if (isDefaultSmsApp(context)) SmsProvider.markRead(context, number)
                                MessageNotifier.cancel(context, number)
                            }
                            closeSheet()
                        },
                        onBlock = {
                            act { graph.settingsStore.block(number) }
                            closeSheet()
                        },
                        onAllow = {
                            act { graph.settingsStore.allow(number) }
                            closeSheet()
                        },
                        onAskDelete = { confirmDelete = true },
                        onConfirmDelete = {
                            act {
                                SmsProvider.deleteConversation(context, number)
                                graph.repos.messages.deleteFor(number.e164)
                                MessageNotifier.cancel(context, number)
                            }
                            closeSheet()
                        },
                        onCancelDelete = { confirmDelete = false },
                    )
                }
            }
        }

        // Hidden under the conversation sheet, which would otherwise be drawn beneath it.
        if (!sheetOpen) {
            RoundKey(
                onClick = onCompose,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 20.dp),
                size = 60.dp,
                containerColor = CgColors.Red,
                contentColor = CgColors.White,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "New message")
            }
        }
    }
}

/** Reads the provider and the message log, then splits and names the conversations. Runs off the main thread. */
private fun loadModel(
    context: Context,
    graph: AppGraph,
): MessagesModel {
    val settings = graph.settingsStore.settings.value
    val conversations = groupConversations(SmsProvider.conversationRows(context))
    val split =
        splitConversations(
            conversations = conversations,
            logs = graph.repos.messages.recent(Int.MAX_VALUE),
            allowList = settings.allowList,
            blockList = settings.blockList,
        )

    fun row(c: Conversation) =
        ListRow(
            conversation = c,
            name = graph.resolutionPolicy.resolve(c.number).name,
            blocked = c.number.e164 in settings.blockList,
        )
    return MessagesModel(inbox = split.inbox.map { row(it) }, quiet = split.quiet.map { row(it) })
}

/**
 * The text for an empty list, or null when there is nothing to say: rows are showing, or the first load has not
 * finished. A search with no hits says so. A blank search counts as no search.
 */
internal fun emptyListLabel(
    loaded: Boolean,
    visibleRows: Int,
    query: String,
    quiet: Boolean,
): String? =
    when {
        !loaded || visibleRows > 0 -> null
        query.isNotBlank() -> "NO MATCHES"
        quiet -> "QUIET INBOX IS EMPTY"
        else -> "NO TEXTS YET"
    }

@Composable
private fun MsgHeader(unread: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
    ) {
        Text("MESSAGES", style = CgType.Display, color = CgColors.White)
        Spacer(Modifier.height(6.dp))
        Text(
            if (unread > 0) "SCREENED BY CALLGUARD · $unread UNREAD" else "SCREENED BY CALLGUARD",
            style = CgType.RowSub.copy(fontSize = 11.sp, letterSpacing = 1.5.sp),
            color = CgColors.TextDim,
        )
    }
}

@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(50))
            .background(CgColors.Surface)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = CgColors.TextDim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = CgType.RowSub.copy(color = CgColors.White, fontSize = 13.sp, letterSpacing = 0.5.sp),
            cursorBrush = SolidColor(CgColors.White),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(text = "SEARCH", style = CgType.Label, color = CgColors.TextFaint)
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = CgColors.TextDim,
                modifier = Modifier.size(18.dp).clickable { onQueryChange("") },
            )
        }
    }
}

/** A dark card with one line of caps and a red pill that asks the system for the permission or role. */
@Composable
private fun SmsBanner(
    message: String,
    button: String,
    onRequest: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CgColors.Surface)
            .padding(16.dp),
    ) {
        Text(
            text = message,
            style = CgType.RowSub.copy(fontSize = 10.sp, letterSpacing = 1.sp),
            color = CgColors.TextDim,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(CgColors.Red)
                .clickable(onClick = onRequest)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = button,
                style = CgType.Label.copy(fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 1.sp),
                color = CgColors.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MsgTabs(
    selected: Int,
    inboxUnread: Int,
    quietCount: Int,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth()) {
            MsgTab(
                label = "INBOX",
                count = inboxUnread,
                selected = selected == TAB_INBOX,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(TAB_INBOX) },
            )
            MsgTab(
                label = "QUIET INBOX",
                count = quietCount,
                selected = selected == TAB_QUIET,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(TAB_QUIET) },
            )
        }
        MsgDivider(indent = false)
        Text(
            text = if (selected == TAB_INBOX) "FROM CONTACTS & TRUSTED SENDERS · RED COUNT IS UNREAD" else "HELD-BACK & BLOCKED SENDERS · NO ALERTS",
            style = CgType.Label.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
            color = CgColors.TextFaint,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun MsgTab(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier.clickable(onClick = onClick).padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = CgType.Label,
                color = if (selected) CgColors.White else CgColors.TextDim,
            )
            if (count > 0) {
                Spacer(Modifier.width(8.dp))
                CountBadge(count)
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) CgColors.White else Color.Transparent),
        )
    }
}

/** Red count pill: unread texts on the inbox tab, conversations on the quiet tab. */
@Composable
private fun CountBadge(count: Int) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 18.dp)
            .clip(RoundedCornerShape(50))
            .background(CgColors.Red)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = CgType.Label.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            color = CgColors.White,
        )
    }
}

@Composable
private fun ConversationRowItem(
    row: ListRow,
    quiet: Boolean,
    now: Long,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onMoveToInbox: () -> Unit,
) {
    val c = row.conversation
    val unread = c.unread > 0
    val known = row.name != null
    val bold = if (unread) FontWeight.Bold else FontWeight.Normal
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillAvatar(
            initials = initialsOf(row.name),
            ringColor = if (known) CgColors.White else CgColors.TextFaint,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = (row.name ?: displayNumber(c.number.e164)).uppercase(),
                style = CgType.Row.copy(fontWeight = bold),
                color = CgColors.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = c.snippet,
                style = CgType.RowSub.copy(fontWeight = bold),
                color = if (unread) CgColors.White else CgColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = listTimeLabel(c.timestamp, now),
                style = CgType.RowSub,
                color = if (unread) CgColors.Red else CgColors.TextFaint,
            )
            Spacer(Modifier.height(6.dp))
            if (quiet) {
                OutcomeTag(if (row.blocked) "BLOCKED" else "QUIET")
                Spacer(Modifier.height(6.dp))
                RedPill("Move to inbox", filled = false, onClick = onMoveToInbox)
            } else if (unread) {
                CountBadge(c.unread)
            }
        }
    }
}

/** Actions for one conversation. Delete asks for confirmation inside the sheet before it removes anything. */
@Composable
private fun ConversationSheet(
    row: ListRow,
    quiet: Boolean,
    confirming: Boolean,
    onCall: () -> Unit,
    onMarkRead: () -> Unit,
    onBlock: () -> Unit,
    onAllow: () -> Unit,
    onAskDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    val number = row.conversation.number.e164
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillAvatar(initials = initialsOf(row.name))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = (row.name ?: displayNumber(number)).uppercase(),
                    style = CgType.Row,
                    color = CgColors.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.name != null) {
                    Text(text = displayNumber(number), style = CgType.RowSub, color = CgColors.TextDim, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetPill("Mark read", Icons.Filled.Done, Modifier.weight(1f), onClick = onMarkRead)
            SheetPill("Block", Icons.Filled.Block, Modifier.weight(1f), onClick = onBlock)
        }
        Spacer(Modifier.height(10.dp))
        SheetPill(if (quiet) "Move to inbox" else "Allow", Icons.Filled.Check, Modifier.fillMaxWidth(), onClick = onAllow)
        Spacer(Modifier.height(22.dp))
        if (confirming) {
            Text(
                text = "DELETE THIS CONVERSATION? TEXTS LEAVE THIS PHONE.",
                style = CgType.RowSub.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                color = CgColors.TextDim,
            )
            Spacer(Modifier.height(10.dp))
            // One red element only: the confirm button. Cancel stays neutral.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetPill("Cancel", null, Modifier.weight(1f), onClick = onCancelDelete)
                SheetPill("Delete", Icons.Filled.Delete, Modifier.weight(1f), containerColor = CgColors.Red, onClick = onConfirmDelete)
            }
        } else {
            // The bottom row of the shared action sheet: white call, red delete. Never two red keys side by side.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundKey(onClick = onCall, size = 68.dp, containerColor = CgColors.White, contentColor = CgColors.Black) {
                    Icon(Icons.Filled.Call, contentDescription = "Call")
                }
                RoundKey(onClick = onAskDelete, size = 68.dp, containerColor = CgColors.Red) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete conversation")
                }
            }
        }
    }
}

@Composable
private fun SheetPill(
    label: String,
    icon: ImageVector?,
    modifier: Modifier,
    containerColor: Color = CgColors.SurfaceHigh,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            modifier
                .height(54.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(containerColor)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = CgColors.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label.uppercase(),
            style = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
            color = CgColors.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun MsgDivider(indent: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 80.dp else 0.dp)
            .height(1.dp)
            .background(CgColors.Divider),
    )
}

private fun hasSmsPermission(
    context: Context,
    permission: String,
): Boolean = context.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
