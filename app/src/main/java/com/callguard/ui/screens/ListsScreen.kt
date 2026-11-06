package com.callguard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.CallGuardApp
import com.callguard.core.PhoneNumber
import com.callguard.core.PhoneNumbers
import com.callguard.ui.components.RoundKey
import com.callguard.ui.displayNumber
import com.callguard.ui.initialsOf
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LstKind { ALLOW, BLOCK }

/** The allow and block lists. Opened from the Guard screen; [onBack] returns to it. */
@Composable
fun ListsScreen(onBack: () -> Unit = {}) {
    val graph = CallGuardApp.graph(LocalContext.current)
    val settings by graph.settingsStore.settings.collectAsStateWithLifecycle()
    val names = remember { mutableStateMapOf<String, String?>() }
    var kind by remember { mutableStateOf(LstKind.ALLOW) }
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val allowList = settings.allowList
    val blockList = settings.blockList
    val listed = if (kind == LstKind.ALLOW) allowList else blockList

    // Each number is resolved once on IO; the result stays in [names] for as long as the screen is composed.
    LaunchedEffect(allowList + blockList) {
        for (n in allowList + blockList) {
            if (n !in names) names[n] = withContext(Dispatchers.IO) { graph.resolve(n)?.name }
        }
    }

    val visible =
        listed.sorted().filter { n ->
            query.isBlank() ||
                names[n].orEmpty().contains(query, ignoreCase = true) ||
                displayNumber(n).contains(query, ignoreCase = true) ||
                n.contains(query, ignoreCase = true)
        }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundKey(onClick = onBack, size = 44.dp, outlined = true) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(14.dp))
            Text("LISTS", style = CgType.Display, color = CgColors.White)
        }
        Spacer(Modifier.height(20.dp))

        LstKindSegmented(
            selected = kind,
            allowLabel = "ALLOW · ${allowList.size}",
            blockLabel = "BLOCK · ${blockList.size}",
            onSelect = {
                kind = it
                note = null
            },
        )
        Spacer(Modifier.height(16.dp))

        LstSearchRow(
            query = query,
            onQuery = { query = it },
            onAdd = { showAdd = true },
        )
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (kind == LstKind.ALLOW) "ALWAYS RING & TEXT THROUGH" else "NEVER RING, NEVER TEXT",
                style = CgType.RowSub.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                color = CgColors.TextDim,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, CgColors.TextFaint, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "TOTAL ${visible.size}",
                    style = CgType.Label.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                    color = CgColors.TextDim,
                )
            }
        }

        note?.let {
            Text(
                text = it,
                style = CgType.RowSub.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                color = CgColors.White,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer(Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            if (visible.isEmpty()) {
                item {
                    Text(
                        text = "NOTHING HERE",
                        style = CgType.Label,
                        color = CgColors.TextFaint,
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    )
                }
            }
            items(items = visible, key = { it }) { number ->
                LstRowItem(
                    number = number,
                    name = names[number],
                    kind = kind,
                    onRemove = { graph.settingsStore.unlist(PhoneNumber(number)) },
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 60.dp)
                        .height(1.dp)
                        .background(CgColors.Divider),
                )
            }
        }
    }

    if (showAdd) {
        LstAddDialog(
            allowList = allowList,
            blockList = blockList,
            initialKind = kind,
            onDismiss = { showAdd = false },
            onConfirm = { number, target ->
                val from = if (target == LstKind.ALLOW) LstKind.BLOCK else LstKind.ALLOW
                val fromList = if (from == LstKind.ALLOW) allowList else blockList
                val moved = number.e164 in fromList
                if (target == LstKind.ALLOW) graph.settingsStore.allow(number) else graph.settingsStore.block(number)
                val label = displayNumber(number.e164)
                note = if (moved) "MOVED $label FROM ${from.name} TO ${target.name}" else "ADDED $label TO ${target.name}"
                kind = target
                showAdd = false
            },
        )
    }
}

@Composable
private fun LstKindSegmented(
    selected: LstKind,
    allowLabel: String,
    blockLabel: String,
    onSelect: (LstKind) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(CgColors.Surface)
            .padding(4.dp),
    ) {
        LstKindSegment(
            kind = LstKind.ALLOW,
            label = allowLabel,
            selected = selected == LstKind.ALLOW,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(LstKind.ALLOW) },
        )
        LstKindSegment(
            kind = LstKind.BLOCK,
            label = blockLabel,
            selected = selected == LstKind.BLOCK,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(LstKind.BLOCK) },
        )
    }
}

@Composable
private fun LstKindSegment(
    kind: LstKind,
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bg =
        when {
            !selected -> Color.Transparent
            kind == LstKind.BLOCK -> CgColors.Red
            else -> CgColors.White
        }
    val fg =
        when {
            !selected -> CgColors.TextDim
            kind == LstKind.BLOCK -> CgColors.White
            else -> CgColors.Black
        }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = CgType.Label, color = fg)
    }
}

@Composable
private fun LstSearchRow(
    query: String,
    onQuery: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(50))
                .background(CgColors.Surface)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = CgColors.TextDim,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = CgType.Row.copy(color = CgColors.White, fontSize = 13.sp),
                cursorBrush = SolidColor(CgColors.White),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                text = "SEARCH NAME OR NUMBER",
                                style = CgType.Row.copy(fontSize = 12.sp),
                                color = CgColors.TextFaint,
                            )
                        }
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(CgColors.Red)
                .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add number",
                tint = CgColors.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun LstRowItem(
    number: String,
    name: String?,
    kind: LstKind,
    onRemove: () -> Unit,
) {
    val isSenderId = number.any { it.isLetter() }
    val initials =
        when {
            name != null -> initialsOf(name)
            isSenderId -> number.take(2)
            else -> "#"
        }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 46.dp, height = 30.dp)
                .clip(RoundedCornerShape(50))
                .background(CgColors.SurfaceHigh)
                .border(
                    1.5.dp,
                    if (kind == LstKind.ALLOW) CgColors.White else CgColors.Red,
                    RoundedCornerShape(50),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                style = CgType.Title.copy(fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp),
                color = CgColors.White,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name ?: displayNumber(number),
                style = CgType.Row,
                color = CgColors.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text =
                    when {
                        isSenderId -> "SENDER ID"
                        name != null -> displayNumber(number)
                        else -> "NOT IN CONTACTS"
                    },
                style = CgType.RowSub.copy(fontSize = 11.sp),
                color = CgColors.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(CgColors.Surface)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove",
                tint = CgColors.TextDim,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun LstAddDialog(
    allowList: Set<String>,
    blockList: Set<String>,
    initialKind: LstKind,
    onDismiss: () -> Unit,
    onConfirm: (PhoneNumber, LstKind) -> Unit,
) {
    var number by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(initialKind) }
    var error by remember { mutableStateOf<String?>(null) }

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
            Text("ADD NUMBER", style = CgType.Title, color = CgColors.White)
            Spacer(Modifier.height(16.dp))
            LstKindSegmented(
                selected = target,
                allowLabel = "ALLOW",
                blockLabel = "BLOCK",
                onSelect = { target = it },
            )
            Spacer(Modifier.height(18.dp))
            LstDialogField(
                label = "NUMBER OR SENDER ID",
                value = number,
                keyboard = KeyboardType.Phone,
                onValue = {
                    number = it
                    error = null
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = error ?: " ",
                style = CgType.RowSub.copy(fontSize = 10.sp, letterSpacing = 1.sp),
                color = CgColors.Red,
            )
            Spacer(Modifier.height(12.dp))
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
                        .background(CgColors.White)
                        .clickable {
                            val parsed = PhoneNumbers.normalize(number)
                            val same = if (target == LstKind.ALLOW) allowList else blockList
                            error =
                                when {
                                    parsed == null -> "ENTER A PHONE NUMBER OR SENDER ID"
                                    parsed.e164 in same -> "ALREADY IN ${target.name}"
                                    else -> null
                                }
                            if (parsed != null && error == null) onConfirm(parsed, target)
                        }.padding(horizontal = 22.dp, vertical = 12.dp),
                ) {
                    Text(text = "ADD", style = CgType.Label, color = CgColors.Black)
                }
            }
        }
    }
}

@Composable
private fun LstDialogField(
    label: String,
    value: String,
    keyboard: KeyboardType,
    onValue: (String) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = CgType.Label.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
            color = CgColors.TextDim,
        )
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = CgType.Row.copy(color = CgColors.White, fontSize = 14.sp),
            cursorBrush = SolidColor(CgColors.White),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(CgColors.SurfaceHigh)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
}
