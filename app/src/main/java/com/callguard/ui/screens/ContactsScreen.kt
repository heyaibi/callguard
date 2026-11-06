package com.callguard.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.callguard.contacts.PhoneContact
import com.callguard.contacts.deleteContact
import com.callguard.contacts.hasWriteContacts
import com.callguard.contacts.loadPhonebook
import com.callguard.contacts.sectionOf
import com.callguard.contacts.setStarred
import com.callguard.identity.hasReadContacts
import com.callguard.ui.components.ActionSheet
import com.callguard.ui.components.CgConfirmDialog
import com.callguard.ui.components.CgGutter
import com.callguard.ui.components.CgLabel
import com.callguard.ui.components.CgPill
import com.callguard.ui.components.DotHeading
import com.callguard.ui.components.MonoRow
import com.callguard.ui.components.PillAvatar
import com.callguard.ui.components.RedPill
import com.callguard.ui.components.SectionLetter
import com.callguard.ui.components.SheetAction
import com.callguard.ui.components.SheetOverlay
import com.callguard.ui.displayNumber
import com.callguard.ui.initialsOf
import com.callguard.ui.rememberContactPhoto
import com.callguard.ui.rememberPlaceCall
import com.callguard.ui.shareText
import com.callguard.ui.startSafely
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** A row of the contacts list: a letter header or a contact. */
private sealed interface ListItem {
    data class Header(
        val letter: Char,
    ) : ListItem

    data class Entry(
        val contact: PhoneContact,
    ) : ListItem
}

private val LETTERS: List<Char> = ('A'..'Z').toList()

/**
 * Phone contacts, grouped by letter. A dotted A–Z scroller on the right jumps to a letter (tap or drag), and the
 * active letter's dot is red. "Favourite" limits the list to starred contacts. Tapping a row opens the contact sheet.
 * Calls go through the system dialer; "Text" opens the conversation through [onOpenThread].
 */
@Composable
fun ContactsScreen(
    onOpenThread: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var canRead by remember { mutableStateOf(hasReadContacts(context)) }
    var revision by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var favouritesOnly by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<PhoneContact?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<PhoneContact?>(null) }
    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canRead = hasReadContacts(context) }

    val contacts by produceState(emptyList<PhoneContact>(), canRead, revision) {
        value = if (canRead) withContext(Dispatchers.IO) { loadPhonebook(context) } else emptyList()
    }
    val readPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            canRead = granted
        }
    // Favourites and deletes write to the phonebook. Ask for WRITE_CONTACTS the first time, then run the action.
    val writePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingWrite
            pendingWrite = null
            if (granted) action?.invoke()
        }

    fun withWrite(action: () -> Unit) {
        if (hasWriteContacts(context)) {
            action()
        } else {
            pendingWrite = action
            writePermission.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    val visible =
        remember(contacts, query, favouritesOnly) {
            contacts.filter { (!favouritesOnly || it.starred) && matchesQuery(query, it) }
        }
    val rows = remember(visible) { listItems(visible) }
    val headerIndex =
        remember(rows) {
            rows.mapIndexedNotNull { index, item -> (item as? ListItem.Header)?.let { it.letter to index } }.toMap()
        }
    val listState = rememberLazyListState()
    val activeLetter by remember(rows) {
        derivedStateOf { sectionAt(rows, listState.firstVisibleItemIndex) }
    }

    fun jumpTo(letter: Char) {
        val target = jumpTarget(letter, headerIndex) ?: return
        scope.launch { listState.scrollToItem(target) }
    }

    val placeCall = rememberPlaceCall()
    val selectedPhoto = rememberContactPhoto(selected?.photoUri)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(12.dp))
            DotHeading(
                text = "Contacts",
                style = CgType.Display,
                modifier = Modifier.padding(horizontal = CgGutter),
            )
            Spacer(Modifier.height(16.dp))
            ContactSearch(
                query = query,
                onQuery = { query = it },
                onAdd = {
                    context.startSafely(Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI))
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CgGutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    label = "Favourite",
                    icon = if (favouritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    on = favouritesOnly,
                    onClick = { favouritesOnly = !favouritesOnly },
                )
                CgPill(
                    text = "Total: ${String.format(Locale.ENGLISH, "%,d", contacts.size)}",
                    containerColor = Color.Transparent,
                    contentColor = CgColors.TextDim,
                    borderColor = CgColors.TextFaint,
                )
            }
            Spacer(Modifier.height(4.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when {
                    !canRead -> {
                        Column(modifier = Modifier.padding(horizontal = CgGutter, vertical = 24.dp)) {
                            CgLabel("Contacts stay on this phone. Allow access to see them here.")
                            Spacer(Modifier.height(12.dp))
                            RedPill("Allow contacts", onClick = { readPermission.launch(Manifest.permission.READ_CONTACTS) })
                        }
                    }

                    visible.isEmpty() -> {
                        CgLabel(
                            text = if (contacts.isEmpty()) "No contacts yet" else "Nothing matches",
                            modifier = Modifier.padding(horizontal = CgGutter, vertical = 24.dp),
                        )
                    }

                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(end = 28.dp, bottom = 120.dp),
                        ) {
                            items(
                                items = rows,
                                key = { item ->
                                    when (item) {
                                        is ListItem.Header -> "h-${item.letter}"
                                        is ListItem.Entry -> "c-${item.contact.id}"
                                    }
                                },
                            ) { item ->
                                when (item) {
                                    is ListItem.Header -> {
                                        SectionLetter(item.letter.toString())
                                    }

                                    is ListItem.Entry -> {
                                        ContactRow(
                                            contact = item.contact,
                                            onClick = {
                                                selected = item.contact
                                                sheetOpen = true
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (canRead && visible.isNotEmpty()) {
                    LetterScroller(
                        active = activeLetter,
                        onLetter = { jumpTo(it) },
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(vertical = 16.dp),
                    )
                }
            }
        }

        SheetOverlay(open = sheetOpen, onDismiss = { sheetOpen = false }) {
            selected?.let { c ->
                ActionSheet(
                    name = c.name,
                    number = displayNumber(c.number),
                    initials = initialsOf(c.name),
                    photo = selectedPhoto,
                    actions =
                        listOf(
                            SheetAction("Call", Icons.Filled.Call) {
                                sheetOpen = false
                                placeCall(c.number)
                            },
                            SheetAction("Text", Icons.AutoMirrored.Filled.Message) {
                                sheetOpen = false
                                onOpenThread(c.number)
                            },
                            SheetAction("Video call", Icons.Filled.Videocam, enabled = false) {},
                            SheetAction(
                                label = if (c.starred) "Unfavourite" else "Add to fav.",
                                icon = if (c.starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            ) {
                                sheetOpen = false
                                withWrite {
                                    if (setStarred(context, c.id, !c.starred)) revision++
                                }
                            },
                        ),
                    onDelete = {
                        sheetOpen = false
                        confirmDelete = c
                    },
                    onShare = {
                        sheetOpen = false
                        context.shareText(listOfNotNull(c.name, displayNumber(c.number)).joinToString(" "))
                    },
                    onEdit = {
                        sheetOpen = false
                        context.editContact(c)
                    },
                )
            }
        }
    }

    confirmDelete?.let { c ->
        CgConfirmDialog(
            title = "DELETE CONTACT?",
            body = "${c.name} is removed from this phone.",
            confirmLabel = "DELETE",
            onConfirm = {
                confirmDelete = null
                withWrite {
                    if (deleteContact(context, c.id)) revision++
                }
            },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun ContactRow(
    contact: PhoneContact,
    onClick: () -> Unit,
) {
    val photo = rememberContactPhoto(contact.photoUri)
    MonoRow(
        title = contact.name,
        leading = { PillAvatar(initialsOf(contact.name), photo = photo) },
        onClick = onClick,
    )
}

@Composable
private fun ContactSearch(
    query: String,
    onQuery: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CgGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
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
                                text = "SEARCH",
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
            modifier =
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(CgColors.Red)
                    .clickable(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "New contact",
                tint = CgColors.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    icon: ImageVector,
    on: Boolean,
    onClick: () -> Unit,
) {
    val fg = if (on) CgColors.Black else CgColors.White
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (on) CgColors.White else CgColors.Surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label.uppercase(),
            style = CgType.Label.copy(fontSize = 10.sp, letterSpacing = 1.sp),
            color = fg,
            maxLines = 1,
        )
    }
}

/** Dotted A–Z scroller. Tap or drag anywhere on it; the dot of the active letter is red. */
@Composable
private fun LetterScroller(
    active: Char?,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { offset -> onLetter(letterAt(offset.y, size.height)) })
                }.pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { offset -> onLetter(letterAt(offset.y, size.height)) },
                        onVerticalDrag = { change, _ ->
                            onLetter(letterAt(change.position.y, size.height))
                            change.consume()
                        },
                    )
                },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LETTERS.forEach { letter ->
            val on = letter == active
            Box(
                modifier =
                    Modifier
                        .size(if (on) 6.dp else 4.dp)
                        .clip(CircleShape)
                        .background(if (on) CgColors.Red else CgColors.TextDim),
            )
        }
    }
}

/** The contact sheet's edit action: the contact's editor, or a new contact with the number when it has no lookup key. */
private fun Context.editContact(c: PhoneContact) {
    val intent =
        c.lookupKey?.let { key ->
            Intent(Intent.ACTION_EDIT)
                .setDataAndType(ContactsContract.Contacts.getLookupUri(c.id, key), ContactsContract.Contacts.CONTENT_ITEM_TYPE)
        } ?: Intent(Intent.ACTION_INSERT_OR_EDIT)
            .setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            .putExtra(ContactsContract.Intents.Insert.PHONE, c.number)
    startSafely(intent)
}

/** Matches the name, or the number when the query is digits. */
private fun matchesQuery(
    query: String,
    c: PhoneContact,
): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    if (c.name.contains(q, ignoreCase = true)) return true
    val digits = q.filter(Char::isDigit)
    return digits.isNotEmpty() && q.all { it.isDigit() || it == '+' || it == ' ' || it == '-' } &&
        c.number.filter(Char::isDigit).contains(digits)
}

/** Rows with a header at the start of each letter. [contacts] is already sorted by name. */
private fun listItems(contacts: List<PhoneContact>): List<ListItem> {
    val out = ArrayList<ListItem>(contacts.size + LETTERS.size)
    var current: Char? = null
    for (c in contacts) {
        val letter = sectionOf(c.name)
        if (letter != current) {
            out += ListItem.Header(letter)
            current = letter
        }
        out += ListItem.Entry(c)
    }
    return out
}

/** The letter of the section that holds the row at [firstVisible], or null before the first header. */
private fun sectionAt(
    rows: List<ListItem>,
    firstVisible: Int,
): Char? {
    for (i in minOf(firstVisible, rows.lastIndex) downTo 0) {
        (rows[i] as? ListItem.Header)?.let { return it.letter }
    }
    return null
}

/** Where a jump to [letter] lands: its header, or the next letter that has one, or the previous one. */
private fun jumpTarget(
    letter: Char,
    headers: Map<Char, Int>,
): Int? {
    for (c in letter..'Z') headers[c]?.let { return it }
    for (c in letter downTo 'A') headers[c]?.let { return it }
    return null
}

/** The scroller letter under vertical position [y] of a scroller [height] pixels tall. */
private fun letterAt(
    y: Float,
    height: Int,
): Char {
    if (height <= 0) return 'A'
    val index = (y / height * LETTERS.size).toInt().coerceIn(0, LETTERS.lastIndex)
    return LETTERS[index]
}
