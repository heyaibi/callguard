package com.callguard.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callguard.core.PhoneNumber
import com.callguard.messaging.ContactSuggestion
import com.callguard.messaging.directAddress
import com.callguard.messaging.searchContacts
import com.callguard.ui.components.CgLabel
import com.callguard.ui.components.MonoRow
import com.callguard.ui.components.PillAvatar
import com.callguard.ui.components.RedPill
import com.callguard.ui.displayNumber
import com.callguard.ui.initialsOf
import com.callguard.ui.theme.CgColors
import com.callguard.ui.theme.CgType
import com.callguard.ui.theme.MonoFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * A new message. Type a name or number in the To field: contacts that match appear below, and a typed number can be
 * sent to directly. Choosing one opens its conversation in place of this screen.
 */
@Composable
fun ComposeScreen(onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var contactsGranted by remember { mutableStateOf(hasContactsPermission(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        contactsGranted = granted
    }

    val typed = query.trim()
    val direct: PhoneNumber? = remember(typed) { directAddress(typed) }
    val suggestions by produceState(emptyList<ContactSuggestion>(), typed, contactsGranted) {
        value = withContext(Dispatchers.IO) { searchContacts(context, typed) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun openFirst() {
        (direct ?: suggestions.firstOrNull()?.number)?.let { onOpenThread(it.e164) }
    }

    // The keyboard is applied once, here, to the whole screen: the contact list stays above it.
    Column(Modifier.fillMaxSize().imePadding().background(CgColors.Black)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CgColors.White)
            }
            Text(text = "NEW MESSAGE", style = CgType.Title, color = CgColors.White)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CgLabel("To")
            Spacer(Modifier.width(14.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, letterSpacing = 1.sp, color = CgColors.White),
                cursorBrush = SolidColor(CgColors.White),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { openFirst() }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(text = "NAME OR NUMBER", style = CgType.Label, color = CgColors.TextFaint)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.height(4.dp))

        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            direct?.let { number ->
                item(key = "direct-${number.e164}") {
                    MonoRow(
                        title = "Send to ${displayNumber(number.e164)}",
                        subtitle = "New number",
                        leading = { PillAvatar(initials = "#", ringColor = CgColors.TextDim) },
                        onClick = { onOpenThread(number.e164) },
                    )
                }
            }
            items(suggestions, key = { it.number.e164 }) { suggestion ->
                MonoRow(
                    title = suggestion.name,
                    subtitle = displayNumber(suggestion.number.e164),
                    leading = { PillAvatar(initials = initialsOf(suggestion.name)) },
                    onClick = { onOpenThread(suggestion.number.e164) },
                )
            }
            if (!contactsGranted) {
                item(key = "grant-contacts") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                        CgLabel("Allow contacts to search names")
                        Spacer(Modifier.height(10.dp))
                        RedPill(
                            text = "Grant contacts",
                            onClick = { permission.launch(Manifest.permission.READ_CONTACTS) },
                        )
                    }
                }
            }
            if (typed.isEmpty()) {
                item(key = "hint") {
                    CgLabel(
                        text = "Start typing a name or number",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
            } else if (direct == null && suggestions.isEmpty()) {
                item(key = "no-match") {
                    CgLabel(
                        text = "No contact matches",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
            }
        }
    }
}

private fun hasContactsPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
