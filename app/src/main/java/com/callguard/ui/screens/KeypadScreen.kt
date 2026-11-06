package com.callguard.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callguard.AppGraph
import com.callguard.CallGuardApp
import com.callguard.calllog.RecentCall
import com.callguard.calllog.mergeRecentCalls
import com.callguard.calllog.readSystemCalls
import com.callguard.contacts.PhoneContact
import com.callguard.contacts.loadPhonebook
import com.callguard.core.PhoneNumbers
import com.callguard.identity.Confidence
import com.callguard.identity.SourceKind
import com.callguard.identity.hasReadContacts
import com.callguard.spam.SpamPolicy
import com.callguard.ui.dialMatches
import com.callguard.ui.displayNumber
import com.callguard.ui.initialsOf
import com.callguard.ui.label
import com.callguard.ui.rememberContactPhoto
import com.callguard.ui.rememberPlaceCall
import com.callguard.ui.components.CgGutter
import com.callguard.ui.components.CgLabel
import com.callguard.ui.components.CgPill
import com.callguard.ui.components.DotHeading
import com.callguard.ui.components.MonoRow
import com.callguard.ui.components.PillAvatar
import com.callguard.ui.components.RedPill
import com.callguard.ui.components.RoundKey
import com.callguard.ui.components.ScoreBar
import com.callguard.ui.components.SourceChip
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import com.callguard.ui.theme.DotFont
import com.callguard.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A caller-ID lookup outcome for the card. [number] is E.164 when the input normalized. */
private data class LookupResult(
    val name: String?,
    val number: String,
    val source: SourceKind,
    val confidence: Confidence,
    val spamScore: Int,
)

/** A row above the keypad: a phonebook contact or a recent call whose number matches what was dialed. */
private data class Suggestion(val name: String?, val number: String, val photoUri: String?)

/**
 * The dialer. Matching contacts and recent calls sit above the keypad while digits are typed (or the recent calls
 * when nothing is typed). The centre white key places the call, the left and right keys open Recents and Contacts.
 * "Who is this?" shows the caller-ID lookup in place of the suggestions. [initialNumber] pre-fills the pad when the
 * app is opened from a dial link.
 */
@Composable
fun KeypadScreen(
    initialNumber: String? = null,
    onOpenRecents: () -> Unit = {},
    onOpenContacts: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val graph = CallGuardApp.graph(context)
    val scope = rememberCoroutineScope()
    val guardCalls by graph.repos.calls.observe().collectAsStateWithLifecycle()
    var digits by rememberSaveable { mutableStateOf(initialNumber?.let(::dialDigitsFor).orEmpty()) }
    var card by remember { mutableStateOf<LookupResult?>(null) }
    var askedContacts by rememberSaveable { mutableStateOf(false) }
    var contactsOn by remember { mutableStateOf(hasReadContacts(context)) }
    val placeCall = rememberPlaceCall()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { contactsOn = hasReadContacts(context) }

    val phonebook by produceState(emptyList<PhoneContact>(), contactsOn) {
        value = if (contactsOn) withContext(Dispatchers.IO) { loadPhonebook(context) } else emptyList()
    }
    val recents by produceState(emptyList<RecentCall>(), guardCalls) {
        value = withContext(Dispatchers.IO) { mergeRecentCalls(readSystemCalls(context), guardCalls) }
    }
    val suggestions = remember(digits, phonebook, recents) { suggestionsFor(digits, phonebook, recents) }
    // The number the call key places: normalized when it is a whole number, the typed digits otherwise.
    val dialTarget = PhoneNumbers.normalize(digits)?.e164 ?: digits

    fun lookup() {
        if (digits.isEmpty()) return
        val raw = dialTarget
        scope.launch { card = withContext(Dispatchers.IO) { lookupResult(graph, raw) } }
    }

    // Contacts are read during identity lookup, so ask once, then look up whatever the answer was.
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsOn = granted
        lookup()
    }

    fun whoIsThis() {
        if (digits.isEmpty()) return
        if (askedContacts || contactsOn) {
            lookup()
        } else {
            askedContacts = true
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun addContact() {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                    .putExtra(ContactsContract.Intents.Insert.PHONE, dialTarget),
            )
        }
    }

    fun type(key: String) {
        card = null
        if (digits.length < MAX_DIGITS) digits += key
    }

    fun backspace() {
        card = null
        digits = digits.dropLast(1)
    }

    fun clear() {
        card = null
        digits = ""
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopStart,
        ) {
            val shown = card
            if (shown != null) {
                LookupCard(
                    result = shown,
                    onClose = { card = null },
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                SuggestionList(
                    suggestions = suggestions,
                    typed = digits.isNotEmpty(),
                    onCall = placeCall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        DotHeading(
            text = typedDisplay(digits),
            style = TextStyle(fontFamily = DotFont, fontWeight = FontWeight.Black, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = 1.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CgGutter),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (digits.isNotEmpty()) {
                CgPill(
                    text = "Who is this?",
                    containerColor = Color.Transparent,
                    contentColor = CgColors.White,
                    borderColor = CgColors.TextFaint,
                    onClick = { whoIsThis() },
                )
                Spacer(Modifier.width(12.dp))
                RedPill("+ Add contact", filled = false, dashed = true, onClick = { addContact() })
            }
        }
        Spacer(Modifier.height(12.dp))

        DialPad(
            onKey = { type(it) },
            onBackspace = { backspace() },
            onClear = { clear() },
            onRecents = onOpenRecents,
            onCall = { placeCall(dialTarget) },
            onContacts = onOpenContacts,
        )
        Spacer(Modifier.height(12.dp))
    }
}

private const val MAX_DIGITS = 15

/** Keypad text for the typed digits: +91 national digits, or a number entered with its own plus sign. */
private fun typedDisplay(digits: String): String = when {
    digits.isEmpty() -> "+91"
    digits.startsWith("+") -> "+" + digits.drop(1).chunked(5).joinToString(" ")
    else -> "+91 " + digits.chunked(5).joinToString(" ")
}

/** The keypad text for a number handed in by a dial link: national digits for India, the full number otherwise. */
private fun dialDigitsFor(raw: String): String {
    val e164 = PhoneNumbers.normalize(raw)?.e164 ?: return raw.filter { it.isDigit() }
    return if (e164.startsWith("+91")) e164.removePrefix("+91") else e164
}

/** Contacts first, then recent calls, one row per number. With nothing typed, the most recent calls. */
private fun suggestionsFor(typed: String, contacts: List<PhoneContact>, recents: List<RecentCall>): List<Suggestion> {
    val photoByNumber = contacts.associate { it.number to it.photoUri }
    val out = LinkedHashMap<String, Suggestion>()
    fun add(name: String?, number: String) {
        out.putIfAbsent(number, Suggestion(name, number, photoByNumber[number]))
    }
    if (typed.isEmpty()) {
        recents.take(RECENT_SUGGESTIONS).forEach { add(it.name, it.number) }
    } else {
        contacts.filter { dialMatches(typed, it.name, it.number) }.forEach { add(it.name, it.number) }
        recents.filter { dialMatches(typed, it.name, it.number) }.forEach { add(it.name, it.number) }
    }
    return out.values.take(MAX_SUGGESTIONS)
}

private const val RECENT_SUGGESTIONS = 6
private const val MAX_SUGGESTIONS = 12

@Composable
private fun SuggestionList(
    suggestions: List<Suggestion>,
    typed: Boolean,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) {
        CgLabel(
            text = if (typed) "No matches" else "No recent calls",
            modifier = modifier.padding(horizontal = CgGutter, vertical = 12.dp),
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(suggestions, key = { it.number }) { s ->
            val photo = rememberContactPhoto(s.photoUri)
            MonoRow(
                title = s.name ?: displayNumber(s.number),
                subtitle = if (s.name != null) displayNumber(s.number) else null,
                leading = { PillAvatar(s.name?.let(::initialsOf) ?: "#", photo = photo) },
                trailing = {
                    CgPill(
                        text = "Call",
                        containerColor = CgColors.SurfaceHigh,
                        contentColor = CgColors.White,
                        borderColor = CgColors.White,
                        onClick = { onCall(s.number) },
                    )
                },
                onClick = { onCall(s.number) },
            )
        }
    }
}

/** Identity and spam score for [raw]. Reads the stores, so callers run it off the main thread. */
private fun lookupResult(graph: AppGraph, raw: String): LookupResult {
    val identity = graph.resolve(raw)
    return LookupResult(
        name = identity?.name,
        number = identity?.number?.e164 ?: raw,
        source = identity?.source ?: SourceKind.NONE,
        confidence = identity?.confidence ?: Confidence.NONE,
        spamScore = identity?.let { graph.spamPolicy.assess(it.number).score } ?: 0,
    )
}

@Composable
private fun LookupCard(
    result: LookupResult,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CgGutter)
            .clip(RoundedCornerShape(28.dp))
            .background(CgColors.Surface)
            .clickable(onClick = onClose)
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CgLabel("Who is this?")
            SourceChip(result.source.label(), result.confidence.name)
        }
        DotHeading(
            text = result.name ?: "UNKNOWN",
            style = CgType.Title,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = displayNumber(result.number),
            style = CgType.Row,
            color = CgColors.TextDim,
            maxLines = 1,
        )
        Spacer(Modifier.height(18.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                CgLabel("Confidence")
                Text(
                    text = result.confidence.name,
                    style = CgType.Row,
                    color = CgColors.White,
                    maxLines = 1,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                CgLabel("Spam score")
                Text(
                    text = "${result.spamScore} / 100",
                    style = CgType.Row,
                    color = if (result.spamScore >= SpamPolicy.SPAM_THRESHOLD) CgColors.Red else CgColors.White,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ScoreBar(score = result.spamScore)
    }
}

/** 3 x 4 graphite keys, red backspace (long press clears), and the bottom row of recents, call and contacts. */
@Composable
private fun DialPad(
    onKey: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onRecents: () -> Unit,
    onCall: () -> Unit,
    onContacts: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CgGutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KeyRow {
            DialKey("1", icon = Icons.Filled.Voicemail, onClick = { onKey("1") })
            DialKey("2", "ABC", onClick = { onKey("2") })
            DialKey("3", "DEF", onClick = { onKey("3") })
        }
        KeyRow {
            DialKey("4", "GHI", onClick = { onKey("4") })
            DialKey("5", "JKL", onClick = { onKey("5") })
            DialKey("6", "MNO", onClick = { onKey("6") })
        }
        KeyRow {
            DialKey("7", "PQRS", onClick = { onKey("7") })
            DialKey("8", "TUV", onClick = { onKey("8") })
            DialKey("9", "WXYZ", onClick = { onKey("9") })
        }
        KeyRow {
            DialKey("#", "*", outlined = true, onClick = { onKey("#") })
            DialKey("0", "+", onClick = { onKey("0") })
            RoundKey(onClick = onBackspace, onLongClick = onClear, size = 72.dp, containerColor = CgColors.Red) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Backspace")
            }
        }
        KeyRow {
            RoundKey(onClick = onRecents, size = 72.dp, containerColor = Color.Transparent) {
                Icon(Icons.Filled.History, contentDescription = "Recents", tint = CgColors.White)
            }
            RoundKey(onClick = onCall, size = 76.dp, containerColor = CgColors.White, contentColor = CgColors.Black) {
                Icon(Icons.Filled.Call, contentDescription = "Call")
            }
            RoundKey(onClick = onContacts, size = 72.dp, containerColor = Color.Transparent) {
                Icon(Icons.Filled.Contacts, contentDescription = "Contacts", tint = CgColors.White)
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun DialKey(
    digit: String,
    letters: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    RoundKey(onClick = onClick, size = 72.dp, outlined = outlined) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                style = TextStyle(fontFamily = DotFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 32.sp),
                color = CgColors.White,
            )
            when {
                icon != null -> Icon(icon, contentDescription = null, tint = CgColors.TextDim, modifier = Modifier.size(12.dp))
                letters.isNotEmpty() -> Text(
                    text = letters,
                    style = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 8.sp, letterSpacing = 1.5.sp),
                    color = CgColors.TextDim,
                )
            }
        }
    }
}
