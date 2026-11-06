package com.callguard.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.AppGraph
import com.callguard.CallGuardApp
import com.callguard.call.Dialer
import com.callguard.identity.hasReadContacts
import com.callguard.messaging.isDefaultSmsApp
import com.callguard.prefs.Settings
import com.callguard.prefs.UnknownCallerAction
import com.callguard.ui.components.CgConfirmDialog
import com.callguard.ui.components.RoundKey
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.provider.Settings as AndroidSettings

private enum class CfgTone { OK, BAD, OFF }

private enum class CfgReset { CLEAR, RESET }

private const val WHATSAPP = "com.whatsapp"
private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

/** Permission state shown by the PERMISSIONS rows. Read again on every resume and after each request. */
private data class Perms(
    val dialer: Boolean,
    val smsApp: Boolean,
    val role: Boolean,
    val contacts: Boolean,
    val sms: Boolean,
    val notifications: Boolean,
    val overlay: Boolean,
    val listener: Boolean,
) {
    companion object {
        fun read(context: Context): Perms {
            fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            return Perms(
                dialer = Dialer.isDefaultDialer(context),
                smsApp = isDefaultSmsApp(context),
                role = context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING),
                contacts = hasReadContacts(context),
                sms = granted(Manifest.permission.RECEIVE_SMS) && granted(Manifest.permission.READ_SMS),
                notifications = Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS),
                overlay = AndroidSettings.canDrawOverlays(context),
                listener = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context),
            )
        }
    }
}

/** Settings, opened from the Guard header. [onOpenLists] opens the allow and block lists. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onOpenLists: () -> Unit = {},
) {
    val context = LocalContext.current
    val graph = CallGuardApp.graph(context)
    val store = graph.settingsStore
    val s by store.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var perms by remember { mutableStateOf(Perms.read(context)) }
    var confirm by remember { mutableStateOf<CfgReset?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { perms = Perms.read(context) }
    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            perms = Perms.read(context)
        }
    val roleLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            perms = Perms.read(context)
        }
    val grantContacts: @Composable () -> Unit = {
        CfgStatusPill(
            text = "GRANT CONTACTS",
            tone = CfgTone.BAD,
            onClick = { permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 140.dp),
    ) {
        RoundKey(onClick = onBack, size = 44.dp, outlined = true) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.height(16.dp))
        Text("SETTINGS", style = CgType.Display, color = CgColors.White)

        CfgSection("LISTS") {
            CfgRow(
                title = "ALLOW / BLOCK LISTS",
                subtitle = "NUMBERS THAT ALWAYS RING, OR NEVER",
                onClick = onOpenLists,
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = CgColors.White)
            }
        }

        CfgSection("PROTECTION") {
            CfgRow(
                title = "CONTACTS ONLY",
                subtitle = "NON-CONTACTS: CALLS & TEXTS BLOCKED",
                extra = if (s.contactsOnly && !perms.contacts) grantContacts else null,
            ) { CfgSwitch(checked = s.contactsOnly, onChange = { v -> store.update { it.copy(contactsOnly = v) } }) }
            CfgRow(
                title = "SILENCE MODE",
                subtitle = "UNKNOWN CALLS RING WITHOUT SOUND",
            ) { CfgSwitch(checked = s.silenceMode, onChange = { v -> store.update { it.copy(silenceMode = v) } }) }
            CfgRow(
                title = "IGNORE MODE",
                subtitle = "NO RING OR ALERT FOR UNKNOWN NUMBERS",
            ) { CfgSwitch(checked = s.ignoreMode, onChange = { v -> store.update { it.copy(ignoreMode = v) } }) }
            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text(
                    text = "UNKNOWN CALLERS",
                    style = CgType.Row.copy(fontSize = 13.sp),
                    color = CgColors.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "WHAT HAPPENS TO CALLS NOT IN CONTACTS",
                    style = CfgSub,
                    color = CgColors.TextDim,
                )
                Spacer(Modifier.height(12.dp))
                CfgUnknownSegmented(
                    selected = s.unknownCallerAction,
                    onSelect = { a -> store.update { it.copy(unknownCallerAction = a) } },
                )
            }
            CfgDivider()
            CfgRow(
                title = "BLOCK SPAM",
                subtitle = "BLOCK NUMBERS SCORED 70 OR MORE",
            ) { CfgSwitch(checked = s.blockSpam, onChange = { v -> store.update { it.copy(blockSpam = v) } }) }
            CfgRow(
                title = "SHOW CARD FOR UNKNOWN",
                subtitle = "CALLER CARD OVER OTHER APPS",
            ) { CfgSwitch(checked = s.showCardForUnknown, onChange = { v -> store.update { it.copy(showCardForUnknown = v) } }) }
        }

        CfgSection("IDENTITY SOURCES · ORDER") {
            CfgSourceRow(index = 1, title = "CNAP", detail = "CARRIER CALLER NAME · READY") {
                CfgDragHandle()
            }
            CfgSourceRow(
                index = 2,
                title = "CONTACTS",
                detail = if (perms.contacts) "ON DEVICE · READY" else "ON DEVICE · NEEDS PERMISSION",
            ) { CfgDragHandle() }
            CfgSourceRow(index = 3, title = "GOOGLE CONTACTS", detail = "SYNC WITH GOOGLE ACCOUNT") {
                CfgSwitch(checked = s.googleContacts, onChange = { v -> store.update { it.copy(googleContacts = v) } })
            }
            CfgSourceRow(index = 4, title = "OPERATOR SERVICE", detail = "CARRIER LOOKUP · NO SIGNAL") {
                CfgStatusPill(text = "UNAVAILABLE", tone = CfgTone.OFF)
            }
            CfgSourceRow(index = 5, title = "APP PROFILE", detail = "LEARNED FROM BRIDGED APPS · READY") {
                CfgDragHandle()
            }
            CfgSourceRow(index = 6, title = "CACHE", detail = "LEARNED FROM CALLS & TEXTS · READY") {
                CfgDragHandle()
            }
            CfgSourceRow(index = 7, title = "BUNDLED DB v${graph.bundled.version}", detail = "OFFLINE · READY") {
                CfgDragHandle()
            }
            CfgSourceRow(index = 8, title = "ONLINE LOOKUP", detail = "OFF BY DEFAULT · USES DATA") {
                CfgSwitch(checked = s.onlineLookup, onChange = { v -> store.update { it.copy(onlineLookup = v) } })
            }
        }

        CfgSection("APP BRIDGE") {
            CfgRow(
                title = "WHATSAPP",
                subtitle = "CALLER CARD FOR WHATSAPP CALLS",
            ) {
                CfgSwitch(
                    checked = s.bridgeApps[WHATSAPP] == true,
                    onChange = { v -> store.update { it.copy(bridgeApps = it.bridgeApps + (WHATSAPP to v)) } },
                )
            }
            CfgRow(
                title = "WHATSAPP BUSINESS",
                subtitle = "CALLER CARD FOR BUSINESS ACCOUNT CALLS",
            ) {
                CfgSwitch(
                    checked = s.bridgeApps[WHATSAPP_BUSINESS] == true,
                    onChange = { v -> store.update { it.copy(bridgeApps = it.bridgeApps + (WHATSAPP_BUSINESS to v)) } },
                )
            }
        }

        CfgSection("PERMISSIONS") {
            CfgRow(
                title = "DEFAULT PHONE APP",
                subtitle = "PLACES CALLS & SHOWS THE IN-CALL SCREEN",
                onClick = {
                    if (!perms.dialer) Dialer.requestRoleIntent(context)?.let { roleLauncher.launch(it) }
                },
            ) { CfgStatusPill(text = if (perms.dialer) "HELD" else "NOT HELD", tone = if (perms.dialer) CfgTone.OK else CfgTone.BAD) }
            CfgRow(
                title = "DEFAULT SMS APP",
                subtitle = "STORES TEXTS & SENDS REPLIES",
                onClick = {
                    if (!perms.smsApp) {
                        roleLauncher.launch(
                            context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS),
                        )
                    }
                },
            ) { CfgStatusPill(text = if (perms.smsApp) "HELD" else "NOT HELD", tone = if (perms.smsApp) CfgTone.OK else CfgTone.BAD) }
            CfgRow(
                title = "CALL SCREENING ROLE",
                subtitle = "DEFAULT CALL SCREENER APP",
                onClick = {
                    if (!perms.role) {
                        roleLauncher.launch(
                            context
                                .getSystemService(RoleManager::class.java)
                                .createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING),
                        )
                    }
                },
            ) { CfgStatusPill(text = if (perms.role) "HELD" else "NOT HELD", tone = if (perms.role) CfgTone.OK else CfgTone.BAD) }
            CfgRow(
                title = "CONTACTS",
                subtitle = "NAMES FOR KNOWN CALLERS",
                onClick = { if (!perms.contacts) permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS)) },
            ) { CfgGrantPill(perms.contacts) }
            CfgRow(
                title = "SMS",
                subtitle = "SENDER IDS FOR THE QUIET INBOX",
                onClick = {
                    if (!perms.sms) permLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS))
                },
            ) { CfgGrantPill(perms.sms) }
            CfgRow(
                title = "NOTIFICATIONS",
                subtitle = "ALERTS FOR SCREENED CALLS & TEXTS",
                onClick = {
                    if (!perms.notifications) permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                },
            ) { CfgGrantPill(perms.notifications) }
            CfgRow(
                title = "OVERLAY",
                subtitle = "CALLER CARD OVER OTHER APPS",
                onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                },
            ) { CfgGrantPill(perms.overlay) }
            CfgRow(
                title = "NOTIFICATION ACCESS",
                subtitle = "NEEDED TO READ WHATSAPP CALL ALERTS",
                onClick = { context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            ) { CfgGrantPill(perms.listener) }
        }

        CfgSection("DATA") {
            Spacer(Modifier.height(16.dp))
            CfgActionPill(
                label = "EXPORT LISTS & SETTINGS",
                fill = CgColors.Surface,
                edge = CgColors.Surface,
                textColor = CgColors.White,
                onClick = { scope.launch { exportAndShare(context, graph, s) } },
            )
            Spacer(Modifier.height(12.dp))
            CfgActionPill(
                label = "CLEAR CACHE & LOGS",
                fill = CgColors.Red,
                edge = CgColors.Red,
                textColor = CgColors.White,
                onClick = { confirm = CfgReset.CLEAR },
            )
            Spacer(Modifier.height(12.dp))
            CfgActionPill(
                label = "RESET ALL",
                fill = Color.Transparent,
                edge = CgColors.Red,
                textColor = CgColors.Red,
                onClick = { confirm = CfgReset.RESET },
            )
        }

        Spacer(Modifier.height(36.dp))
        Text(
            text = "CALLGUARD 0.1.0 · DB v${graph.bundled.version} · INDIA",
            style = CfgSub.copy(letterSpacing = 1.5.sp),
            color = CgColors.TextFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    confirm?.let { action ->
        val clear = action == CfgReset.CLEAR
        CgConfirmDialog(
            title = if (clear) "CLEAR CACHE & LOGS?" else "RESET ALL?",
            body =
                if (clear) {
                    "CALL AND MESSAGE LOGS AND LEARNED NAMES GO. YOUR LISTS, SPAM MARKS AND SETTINGS STAY."
                } else {
                    "LISTS AND SETTINGS GO BACK TO DEFAULTS. LOGS AND LEARNED NAMES ARE CLEARED. YOUR SPAM MARKS STAY."
                },
            confirmLabel = if (clear) "CLEAR" else "RESET",
            onConfirm = {
                confirm = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        graph.repos.clearAll()
                        if (action == CfgReset.RESET) store.reset()
                    }
                }
            },
            onDismiss = { confirm = null },
        )
    }
}

/** Writes settings and every stored record to one JSON file in the app's files dir, then opens the share sheet. */
private suspend fun exportAndShare(
    context: Context,
    graph: AppGraph,
    settings: Settings,
) {
    val uri =
        withContext(Dispatchers.IO) {
            val json =
                JSONObject()
                    .put("settings", settings.toJson())
                    .put("data", JSONObject(graph.repos.exportJson()))
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = File(dir, "callguard-export-${System.currentTimeMillis()}.json")
            file.writeText(json.toString(2))
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

private fun Settings.toJson(): JSONObject =
    JSONObject()
        .put("contactsOnly", contactsOnly)
        .put("silenceMode", silenceMode)
        .put("ignoreMode", ignoreMode)
        .put("unknownCallerAction", unknownCallerAction.name)
        .put("blockSpam", blockSpam)
        .put("showCardForUnknown", showCardForUnknown)
        .put("onlineLookup", onlineLookup)
        .put("googleContacts", googleContacts)
        .put("bridgeApps", JSONObject(bridgeApps))
        .put("allowList", JSONArray(allowList.toList()))
        .put("blockList", JSONArray(blockList.toList()))

private val CfgSub = CgType.RowSub.copy(fontSize = 10.sp, letterSpacing = 0.8.sp)

@Composable
private fun CfgSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = CgType.Label, color = CgColors.TextDim)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).height(1.dp).background(CgColors.Divider))
        }
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun CfgDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(CgColors.Divider))
}

@Composable
private fun CfgRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 14.dp)) {
                Text(text = title, style = CgType.Row.copy(fontSize = 13.sp), color = CgColors.White)
                Spacer(Modifier.height(4.dp))
                Text(text = subtitle, style = CfgSub, color = CgColors.TextDim)
                if (extra != null) {
                    Spacer(Modifier.height(10.dp))
                    extra()
                }
            }
            trailing()
        }
        CfgDivider()
    }
}

@Composable
private fun CfgSourceRow(
    index: Int,
    title: String,
    detail: String,
    trailing: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = index.toString().padStart(2, '0'),
                style = CgType.Title.copy(fontSize = 18.sp, lineHeight = 20.sp),
                color = CgColors.TextDim,
                modifier = Modifier.width(38.dp),
            )
            Column(Modifier.weight(1f).padding(end = 14.dp)) {
                Text(text = title, style = CgType.Row.copy(fontSize = 13.sp), color = CgColors.White)
                Spacer(Modifier.height(4.dp))
                Text(text = detail, style = CfgSub, color = CgColors.TextDim)
            }
            trailing()
        }
        CfgDivider()
    }
}

@Composable
private fun CfgDragHandle() {
    Column(
        Modifier.size(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(3) { i ->
            Box(Modifier.width(16.dp).height(2.dp).background(CgColors.TextFaint))
            if (i < 2) Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun CfgSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val track by animateColorAsState(if (checked) CgColors.Red else CgColors.SurfaceHigh)
    val thumbX by animateDpAsState(if (checked) 22.dp else 2.dp)
    Box(
        Modifier
            .size(width = 50.dp, height = 28.dp)
            .clip(RoundedCornerShape(50))
            .background(track)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(CgColors.White),
        )
    }
}

@Composable
private fun CfgGrantPill(granted: Boolean) {
    CfgStatusPill(
        text = if (granted) "GRANTED" else "NOT GRANTED",
        tone = if (granted) CfgTone.OK else CfgTone.BAD,
    )
}

@Composable
private fun CfgStatusPill(
    text: String,
    tone: CfgTone,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (tone == CfgTone.BAD) CgColors.Red else Color.Transparent
    val edge =
        when (tone) {
            CfgTone.OK -> CgColors.White
            CfgTone.BAD -> CgColors.Red
            CfgTone.OFF -> CgColors.TextFaint
        }
    val fg = if (tone == CfgTone.OFF) CgColors.TextDim else CgColors.White
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, edge, RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = CgType.Label.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
            color = fg,
        )
    }
}

@Composable
private fun CfgUnknownSegmented(
    selected: UnknownCallerAction,
    onSelect: (UnknownCallerAction) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(CgColors.Surface)
            .padding(4.dp),
    ) {
        CfgSegment(
            label = "SILENCE",
            selected = selected == UnknownCallerAction.SILENCE,
            selectedBg = CgColors.White,
            selectedFg = CgColors.Black,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(UnknownCallerAction.SILENCE) },
        )
        CfgSegment(
            label = "BLOCK",
            selected = selected == UnknownCallerAction.BLOCK,
            selectedBg = CgColors.Red,
            selectedFg = CgColors.White,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(UnknownCallerAction.BLOCK) },
        )
    }
}

@Composable
private fun CfgSegment(
    label: String,
    selected: Boolean,
    selectedBg: Color,
    selectedFg: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) selectedBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CgType.Label,
            color = if (selected) selectedFg else CgColors.TextDim,
        )
    }
}

@Composable
private fun CfgActionPill(
    label: String,
    fill: Color,
    edge: Color,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(fill)
            .border(1.dp, edge, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = CgType.Label.copy(fontSize = 12.sp, letterSpacing = 1.5.sp),
            color = textColor,
        )
    }
}
