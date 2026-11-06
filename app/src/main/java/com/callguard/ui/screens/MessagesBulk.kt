package com.callguard.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callguard.AppGraph
import com.callguard.messaging.ListFilter
import com.callguard.messaging.MessageNotifier
import com.callguard.messaging.SmsProvider
import com.callguard.messaging.isDefaultSmsApp
import com.callguard.messaging.kindLabel
import com.callguard.spam.classify.SmsKind
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import com.callguard.ui.theme.MonoFont

/** Bottom space the list keeps while the selection bar covers its end. The bar grows when it asks for confirmation. */
internal val selectionBarClearance: Dp = 220.dp

private const val DISABLED_ALPHA = 0.4f

/**
 * Selection mode of the Messages list. While it is on, the picked rows take one bulk action. A row is picked by its
 * number, so a pick survives a reload of the list.
 */
internal class Selection {
    /** True while selection mode is on. */
    var active by mutableStateOf(false)
        private set

    /** The picked numbers, in e164 form. A pick on a row that is out of view stays until selection ends. */
    var picked by mutableStateOf<Set<String>>(emptySet())
        private set

    /** True after DELETE is pressed, until the user confirms or cancels. */
    var confirmingDelete by mutableStateOf(false)

    fun start() {
        active = true
        picked = emptySet()
        confirmingDelete = false
    }

    fun stop() {
        active = false
        picked = emptySet()
        confirmingDelete = false
    }

    fun toggle(key: String) {
        picked = if (key in picked) picked - key else picked + key
    }

    /** Picks every visible row. When every visible row is picked already, it clears them instead. */
    fun toggleAll(visible: List<ListRow>) {
        val keys = visible.mapTo(mutableSetOf()) { it.conversation.number.e164 }
        picked = if (allPicked(visible)) picked - keys else picked + keys
    }

    /** The visible rows that are picked. */
    fun chosen(visible: List<ListRow>): List<ListRow> = visible.filter { it.conversation.number.e164 in picked }

    /** True when there is a visible row and every visible row is picked. */
    fun allPicked(visible: List<ListRow>): Boolean = visible.isNotEmpty() && chosen(visible).size == visible.size

    /** Ends selection and returns the picked visible rows. It returns none when selection is off. */
    fun take(visible: List<ListRow>): List<ListRow> {
        if (!active) return emptyList()
        val rows = chosen(visible)
        stop()
        return rows
    }
}

/** Keeps the filter across a rotation as names: one name per picked kind. */
internal val listFilterSaver: Saver<ListFilter, Any> =
    listSaver<ListFilter, String>(
        save = { it.kinds.map { kind -> kind.name } },
        restore = { saved -> ListFilter(kinds = saved.mapTo(mutableSetOf()) { SmsKind.valueOf(it) }) },
    )

/**
 * The kind chips under the tabs: one chip per kind in [kinds], which are the kinds the open tab holds. Any number of
 * kinds may be picked, and none means every kind.
 */
@Composable
internal fun FilterBar(
    kinds: List<SmsKind>,
    filter: ListFilter,
    onChange: (ListFilter) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        kinds.forEach { kind ->
            FilterChip(kindLabel(kind), selected = kind in filter.kinds, role = Role.Checkbox) {
                onChange(filter.toggleKind(kind))
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    role: Role,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (selected) CgColors.White else CgColors.Surface)
            .selectable(selected = selected, role = role, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CgType.Label.copy(fontSize = 10.sp, letterSpacing = 1.sp),
            color = if (selected) CgColors.Black else CgColors.TextDim,
            maxLines = 1,
        )
    }
}

/**
 * The caption line under the tabs. It says what the tab holds, and SELECT sits at its right end. While selecting, it
 * shows the chosen count, with ALL (choose every visible row, or clear them when all are chosen) and DONE.
 */
@Composable
internal fun TabCaption(
    caption: String,
    selecting: Boolean,
    chosenCount: Int,
    onStartSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onDoneSelect: () -> Unit,
) {
    val style = CgType.Label.copy(fontSize = 9.sp, letterSpacing = 1.2.sp)
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Text(text = "$chosenCount SELECTED", style = style, color = CgColors.White, modifier = Modifier.weight(1f))
            CaptionAction("ALL", onSelectAll)
            CaptionAction("DONE", onDoneSelect)
        } else {
            Text(text = caption, style = style, color = CgColors.TextFaint, modifier = Modifier.weight(1f))
            CaptionAction("SELECT", onStartSelect)
        }
    }
}

@Composable
private fun CaptionAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = CgType.Label.copy(fontSize = 10.sp, letterSpacing = 1.sp),
        color = CgColors.White,
        modifier =
            Modifier
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

/** The round mark that replaces a row's avatar in selection mode: a white check when picked, a faint ring when not. */
@Composable
internal fun SelectionMark(selected: Boolean) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) CgColors.White else Color.Transparent)
            .border(1.dp, if (selected) CgColors.White else CgColors.TextFaint, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = CgColors.Black, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * The bottom bar in selection mode. It acts on the chosen rows, in two rows: READ, BLOCK and ALLOW (INBOX on the quiet
 * tab), then SPAM, NOT SPAM and DELETE. DELETE asks first. The second row then gives way to Cancel and Delete, so
 * only one red element shows. Every action is disabled while no row is chosen.
 */
@Composable
internal fun SelectionBar(
    modifier: Modifier,
    quiet: Boolean,
    chosen: Int,
    confirming: Boolean,
    onRead: () -> Unit,
    onBlock: () -> Unit,
    onAllow: () -> Unit,
    onSpam: () -> Unit,
    onNotSpam: () -> Unit,
    onAskDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
) {
    val enabled = chosen > 0
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(CgColors.Surface)
            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(top = 12.dp)
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(CgColors.TextFaint),
        )
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BulkPill("Read", Modifier.weight(1f), enabled, onClick = onRead)
            BulkPill("Block", Modifier.weight(1f), enabled, onClick = onBlock)
            BulkPill(if (quiet) "Inbox" else "Allow", Modifier.weight(1f), enabled, onClick = onAllow)
        }
        Spacer(Modifier.height(8.dp))
        if (confirming) {
            val noun = if (chosen == 1) "CONVERSATION" else "CONVERSATIONS"
            Text(
                text = "DELETE $chosen $noun? TEXTS LEAVE THIS PHONE.",
                style = CgType.RowSub.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                color = CgColors.TextDim,
            )
            Spacer(Modifier.height(10.dp))
            // One red element only: the confirm button. Cancel stays neutral.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BulkPill("Cancel", Modifier.weight(1f), enabled = true, onClick = onCancelDelete)
                BulkPill(
                    "Delete",
                    Modifier.weight(1f),
                    enabled,
                    containerColor = CgColors.Red,
                    onClick = onConfirmDelete,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BulkPill("Spam", Modifier.weight(1f), enabled, onClick = onSpam)
                BulkPill("Not spam", Modifier.weight(1f), enabled, onClick = onNotSpam)
                BulkPill("Delete", Modifier.weight(1f), enabled, containerColor = CgColors.Red, onClick = onAskDelete)
            }
        }
    }
}

/** A pill button for the selection bar, styled like the sheet pills. Disabled, it dims and ignores taps. */
@Composable
private fun BulkPill(
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    containerColor: Color = CgColors.SurfaceHigh,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .height(48.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clip(RoundedCornerShape(percent = 50))
                .background(containerColor)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style =
                TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
            color = CgColors.White,
            maxLines = 1,
        )
    }
}

/** Marks one conversation read and clears its notification. Runs off the main thread. */
internal fun markRowRead(
    context: Context,
    row: ListRow,
) {
    val number = row.conversation.number
    if (isDefaultSmsApp(context)) SmsProvider.markRead(context, number)
    MessageNotifier.cancel(context, number)
}

/**
 * Records the user's spam or not-spam mark for one conversation, then teaches the classifier its latest received text.
 * Runs off the main thread.
 */
internal fun markRowSpam(
    graph: AppGraph,
    row: ListRow,
    spam: Boolean,
) {
    graph.spamPolicy.mark(row.conversation.number, spam)
    row.incoming?.let { graph.learnSms(it.sender, it.body, spam) }
}

/**
 * Deletes one conversation: its texts in the provider, its log entries, and its notification. Runs off the main
 * thread.
 */
internal fun deleteRow(
    context: Context,
    graph: AppGraph,
    row: ListRow,
) {
    val number = row.conversation.number
    SmsProvider.deleteConversation(context, number)
    graph.repos.messages.deleteFor(number.e164)
    MessageNotifier.cancel(context, number)
}
