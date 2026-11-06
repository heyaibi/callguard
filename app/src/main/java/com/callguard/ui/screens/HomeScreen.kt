package com.callguard.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings as GearIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.CallGuardApp
import com.callguard.identity.confidence
import com.callguard.identity.hasReadContacts
import com.callguard.ui.components.CgDivider
import com.callguard.ui.components.CgGutter
import com.callguard.ui.components.CgLabel
import com.callguard.ui.components.CgPill
import com.callguard.ui.components.CgToggle
import com.callguard.ui.components.DotHeading
import com.callguard.ui.components.MonoRow
import com.callguard.ui.components.PillAvatar
import com.callguard.ui.components.RedPill
import com.callguard.ui.components.RoundKey
import com.callguard.ui.components.SourceChip
import com.callguard.ui.displayNumber
import com.callguard.ui.initialsOf
import com.callguard.ui.label
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import com.callguard.ui.timeLabel
import com.callguard.ui.todayStats

/**
 * Home: protection status, today's counts, quick modes and the last identified callers.
 * Status is read from the platform on every resume. Quick modes read and write the persisted settings.
 * [onPreviewCall] is the hidden debug entry into the incoming-call screen.
 */
@Composable
fun HomeScreen(
    onPreviewCall: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val graph = CallGuardApp.graph(context)
    val settings by graph.settingsStore.settings.collectAsStateWithLifecycle()
    val calls by graph.repos.calls.observe().collectAsStateWithLifecycle()
    var screeningOn by remember { mutableStateOf(isScreeningHeld(context)) }
    var bridgeOn by remember { mutableStateOf(isBridgeEnabled(context)) }
    var contactsOn by remember { mutableStateOf(hasReadContacts(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        screeningOn = isScreeningHeld(context)
        bridgeOn = isBridgeEnabled(context)
        contactsOn = hasReadContacts(context)
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        screeningOn = isScreeningHeld(context)
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        contactsOn = hasReadContacts(context)
    }
    val stats = todayStats(calls)
    val identified = calls.filter { it.name != null }.take(3)
    // Contacts only does nothing without READ_CONTACTS, so the protection is incomplete until it is granted.
    val needsContacts = settings.contactsOnly && !contactsOn
    val pending = listOf(screeningOn, bridgeOn, !needsContacts).count { !it }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CgGutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DotHeading(
                text = "CallGuard",
                style = CgType.DisplayXL,
                modifier = Modifier.weight(1f),
            )
            RoundKey(onClick = onOpenSettings, size = 44.dp) {
                Icon(Icons.Filled.GearIcon, contentDescription = "Settings")
            }
        }
        CgLabel(
            text = "India · caller id + spam shield",
            modifier = Modifier.padding(horizontal = CgGutter, vertical = 8.dp),
        )

        // Protection status card
        Column(
            modifier = Modifier
                .padding(horizontal = CgGutter, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(CgColors.Surface)
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CgLabel("Protection")
                CgLabel(if (pending == 0) "All on" else "Action needed")
            }
            DotHeading(
                text = if (pending == 0) "PROTECTED" else "$pending TO SET UP",
                style = CgType.Title,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(10.dp))
            StatusLine(
                label = "Call screening role",
                on = screeningOn,
                onSetUp = {
                    roleLauncher.launch(
                        context.getSystemService(RoleManager::class.java)
                            .createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                    )
                },
            )
            CgDivider()
            StatusLine(
                label = "WhatsApp bridge",
                on = bridgeOn,
                onSetUp = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            )
            if (needsContacts) {
                CgDivider()
                ActionLine(
                    label = "Contacts access",
                    action = "Grant contacts",
                    onAction = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                )
            }
        }

        // Today's counts
        CgLabel("Today", modifier = Modifier.padding(start = CgGutter, end = CgGutter, top = 12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CgGutter, vertical = 8.dp),
        ) {
            StatCell(stats.identified.toString(), "Identified", Modifier.weight(1f))
            StatCell(stats.silenced.toString(), "Silenced", Modifier.weight(1f))
            StatCell(stats.blocked.toString(), "Blocked", Modifier.weight(1f), accent = CgColors.Red)
        }

        // Quick modes
        CgLabel("Quick modes", modifier = Modifier.padding(start = CgGutter, end = CgGutter, top = 20.dp))
        Spacer(Modifier.height(4.dp))
        CgDivider()
        ModeLine(
            title = "Silence mode",
            subtitle = "Unknown callers go quiet",
            checked = settings.silenceMode,
            onChange = { on -> graph.settingsStore.update { it.copy(silenceMode = on) } },
        )
        CgDivider()
        ModeLine(
            title = "Ignore mode",
            subtitle = "Non-contact texts skip the inbox",
            checked = settings.ignoreMode,
            onChange = { on -> graph.settingsStore.update { it.copy(ignoreMode = on) } },
        )
        CgDivider()

        // Last identified callers
        CgLabel("Last identified", modifier = Modifier.padding(start = CgGutter, end = CgGutter, top = 24.dp, bottom = 4.dp))
        if (identified.isEmpty()) {
            CgLabel("Nothing identified yet", modifier = Modifier.padding(horizontal = CgGutter, vertical = 12.dp))
        }
        identified.forEach { call ->
            MonoRow(
                title = call.name.orEmpty(),
                subtitle = "${displayNumber(call.number)} · ${timeLabel(call.timestamp)}",
                leading = { PillAvatar(initialsOf(call.name)) },
                trailing = { SourceChip(call.source.label(), call.source.confidence.name) },
            )
        }

        // Hidden debug entry: opens the incoming-call preview.
        Text(
            text = "PREVIEW CALL",
            style = CgType.Label,
            color = CgColors.TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onPreviewCall)
                .padding(vertical = 28.dp),
        )
    }
}

/** Call screening is held by this app when the platform says so. */
private fun isScreeningHeld(context: Context): Boolean =
    context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

/** WhatsApp bridge is on when this app is enabled as a notification listener. */
private fun isBridgeEnabled(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

@Composable
private fun StatusLine(label: String, on: Boolean, onSetUp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !on, onClick = onSetUp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = CgType.Row, color = CgColors.White)
        if (on) {
            CgPill("On", containerColor = Color.Transparent, contentColor = CgColors.White, borderColor = CgColors.White)
        } else {
            RedPill("Set up")
        }
    }
}

/** A status row whose only control is a red action pill, for a permission the user still has to allow. */
@Composable
private fun ActionLine(label: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = CgType.Row, color = CgColors.White)
        RedPill(action, onClick = onAction)
    }
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier, accent: Color = CgColors.White) {
    Column(modifier = modifier) {
        DotHeading(text = value, style = CgType.Display, color = accent)
        CgLabel(label)
    }
}

@Composable
private fun ModeLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CgGutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title.uppercase(), style = CgType.Row, color = CgColors.White)
            Text(subtitle.uppercase(), style = CgType.RowSub, color = CgColors.TextDim)
        }
        CgToggle(checked = checked, onCheckedChange = onChange)
    }
}
