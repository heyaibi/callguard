package com.callguard.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Report
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.CallGuardApp
import com.callguard.calllog.CallKind
import com.callguard.calllog.RecentCall
import com.callguard.calllog.hasReadCallLog
import com.callguard.calllog.mergeRecentCalls
import com.callguard.calllog.readSystemCalls
import com.callguard.core.PhoneNumber
import com.callguard.core.PhoneNumbers
import com.callguard.identity.SourceKind
import com.callguard.identity.confidence
import com.callguard.ui.components.ActionSheet
import com.callguard.ui.components.CgGutter
import com.callguard.ui.components.CgLabel
import com.callguard.ui.components.CgPill
import com.callguard.ui.components.DotHeading
import com.callguard.ui.components.MonoRow
import com.callguard.ui.components.OutcomeTag
import com.callguard.ui.components.PillAvatar
import com.callguard.ui.components.RedPill
import com.callguard.ui.components.SectionLetter
import com.callguard.ui.components.SheetAction
import com.callguard.ui.components.SheetOverlay
import com.callguard.ui.components.SourceChip
import com.callguard.ui.dayLabel
import com.callguard.ui.displayNumber
import com.callguard.ui.initialsOf
import com.callguard.ui.label
import com.callguard.ui.rememberPlaceCall
import com.callguard.ui.shareText
import com.callguard.ui.startSafely
import com.callguard.ui.timeLabel
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private enum class RecentFilter(val label: String) {
    ALL("All"),
    SILENCED("Silenced"),
    BLOCKED("Blocked"),
    SPAM("Spam");

    fun keeps(call: RecentCall, spam: Boolean): Boolean = when (this) {
        ALL -> true
        SILENCED -> call.guard?.outcome == "SILENCED"
        BLOCKED -> call.kind == CallKind.BLOCKED || call.guard?.outcome == "BLOCKED"
        SPAM -> spam
    }
}

/**
 * Call history, newest first, grouped by day. It merges the phone's call log with CallGuard's own records, so a
 * silenced or blocked call keeps its tag and source. Missed calls have a red name. Tapping a row calls back;
 * long-pressing opens the action sheet.
 */
@Composable
fun RecentsScreen(
    onOpenThread: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val graph = CallGuardApp.graph(context)
    val guardCalls by graph.repos.calls.observe().collectAsStateWithLifecycle()
    val placeCall = rememberPlaceCall()
    var filter by remember { mutableStateOf(RecentFilter.ALL) }
    var sheetCall by remember { mutableStateOf<RecentCall?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var spamRevision by remember { mutableIntStateOf(0) }
    var hasLog by remember { mutableStateOf(hasReadCallLog(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasLog = hasReadCallLog(context) }
    val logPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLog = granted
    }

    val recents by produceState(emptyList<RecentCall>(), guardCalls, hasLog) {
        value = withContext(Dispatchers.IO) { mergeRecentCalls(readSystemCalls(context), guardCalls) }
    }
    // Spam is decided by the policy, which reads the database, so it is computed off the main thread.
    val spamByNumber by produceState(emptyMap<String, Boolean>(), recents, spamRevision) {
        value = withContext(Dispatchers.IO) {
            recents.map { it.number }.distinct().associateWith { graph.spamPolicy.assess(PhoneNumber(it)).isSpam }
        }
    }

    val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = recents.filter { it.timestamp >= startOfDay }
    val todayBlocked = today.count { it.kind == CallKind.BLOCKED || it.guard?.outcome == "BLOCKED" }
    val visible = recents.filter { filter.keeps(it, spamByNumber[it.number] == true) }
    val groups = visible.groupBy { dayLabel(it.timestamp) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(12.dp))
            DotHeading(
                text = "Recents",
                style = CgType.Display,
                modifier = Modifier.padding(horizontal = CgGutter),
            )
            CgLabel(
                text = "${today.size} calls today · $todayBlocked blocked",
                modifier = Modifier.padding(horizontal = CgGutter, vertical = 8.dp),
            )
            Row(
                modifier = Modifier.padding(horizontal = CgGutter, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecentFilter.entries.forEach { option ->
                    val on = option == filter
                    CgPill(
                        text = option.label,
                        containerColor = if (on) CgColors.White else CgColors.Surface,
                        contentColor = if (on) CgColors.Black else CgColors.TextDim,
                        onClick = { filter = option },
                    )
                }
            }
            if (!hasLog) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CgGutter, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CgLabel("Phone call history is off")
                    RedPill("Allow call log", onClick = { logPermission.launch(Manifest.permission.READ_CALL_LOG) })
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                groups.forEach { (day, rows) ->
                    item(key = "header-$day") { SectionLetter(day) }
                    items(rows) { call ->
                        val spam = spamByNumber[call.number] == true
                        RecentRow(
                            call = call,
                            spam = spam,
                            onClick = { placeCall(call.number) },
                            onLongClick = {
                                sheetCall = call
                                sheetOpen = true
                            },
                        )
                    }
                }
                if (recents.isEmpty()) {
                    item(key = "empty") {
                        CgLabel(
                            text = "No calls yet",
                            modifier = Modifier.padding(horizontal = CgGutter, vertical = 24.dp),
                        )
                    }
                } else if (groups.isEmpty()) {
                    item(key = "filtered") {
                        CgLabel(
                            text = "Nothing in this filter",
                            modifier = Modifier.padding(horizontal = CgGutter, vertical = 24.dp),
                        )
                    }
                }
            }
        }

        SheetOverlay(open = sheetOpen, onDismiss = { sheetOpen = false }) {
            sheetCall?.let { call ->
                val n = PhoneNumbers.normalize(call.number)
                val onDelete: (() -> Unit)? = call.guard?.let { entry ->
                    {
                        graph.repos.calls.delete(entry.id)
                        sheetOpen = false
                    }
                }
                ActionSheet(
                    name = call.name,
                    number = displayNumber(call.number),
                    initials = initialsOf(call.name),
                    actions = listOf(
                        SheetAction("Call", Icons.Filled.Call) {
                            sheetOpen = false
                            placeCall(call.number)
                        },
                        SheetAction("Text", Icons.AutoMirrored.Filled.Message) {
                            sheetOpen = false
                            onOpenThread(n?.e164 ?: call.number)

                        },
                        SheetAction("Mark spam", Icons.Filled.Report) {
                            sheetOpen = false
                            n?.let {
                                graph.spamPolicy.mark(it, true)
                                spamRevision++
                            }
                        },
                        SheetAction("Block", Icons.Filled.Block) {
                            sheetOpen = false
                            n?.let(graph.settingsStore::block)
                        },
                        SheetAction("Allow", Icons.Filled.Check) {
                            sheetOpen = false
                            n?.let(graph.settingsStore::allow)
                        },
                    ),
                    onDelete = onDelete,
                    onShare = {
                        sheetOpen = false
                        context.shareText(listOfNotNull(call.name, displayNumber(call.number)).joinToString(" "))
                    },
                    onEdit = {
                        sheetOpen = false
                        context.editNumber(call.number)
                    },
                )
            }
        }
    }
}

@Composable
private fun RecentRow(
    call: RecentCall,
    spam: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    MonoRow(
        title = call.name ?: "Unknown number",
        titleColor = if (call.kind == CallKind.MISSED) CgColors.Red else CgColors.White,
        subtitle = "${displayNumber(call.number)} · ${timeLabel(call.timestamp)}${durationSuffix(call.durationSec)}",
        leading = { PillAvatar(initialsOf(call.name)) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                CallTag(call, spam)
                val guard = call.guard
                if (guard != null && guard.source != SourceKind.NONE) {
                    Spacer(Modifier.height(6.dp))
                    SourceChip(guard.source.label(), guard.source.confidence.name)
                }
            }
        },
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/** The tag for a row: spam, then CallGuard's outcome, then the phone's call type. */
@Composable
private fun CallTag(call: RecentCall, spam: Boolean) {
    val guard = call.guard
    when {
        spam -> RedPill("Spam", filled = false)
        guard != null -> OutcomeTag(guard.outcome)
        call.kind == CallKind.BLOCKED -> RedPill("Blocked")
        call.kind == CallKind.MISSED -> RedPill("Missed", filled = false)
        call.kind == CallKind.REJECTED -> CgPill("Rejected", containerColor = Color.Transparent, contentColor = CgColors.TextDim, borderColor = CgColors.TextFaint)
        call.kind == CallKind.OUTGOING -> CgPill("Outgoing")
        else -> CgPill("Incoming")
    }
}

private fun durationSuffix(seconds: Long): String {
    if (seconds <= 0) return ""
    return " · ${seconds / 60}:${String.format(Locale.ENGLISH, "%02d", seconds % 60)}"
}

/** Opens the contact editor for [number], or a new contact with it. */
private fun Context.editNumber(number: String) {
    startSafely(
        Intent(Intent.ACTION_INSERT_OR_EDIT)
            .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, number),
    )
}
