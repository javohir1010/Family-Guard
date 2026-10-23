package com.familyguard.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.app.data.model.Contact
import com.familyguard.app.data.model.UserProfile
import com.familyguard.app.ui.map.FamilyMap
import com.familyguard.app.ui.map.MapMarker
import com.familyguard.app.ui.theme.LocalFgColors
import com.familyguard.app.ui.theme.ThemeMode
import com.familyguard.app.ui.theme.ThemePrefs
import com.familyguard.app.viewmodel.ChildHomeViewModel
import com.familyguard.app.viewmodel.SosUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * The child's screen: profile and theme on top, the family map, the SOS
 * button in the middle and quick-dial contacts at the bottom. No logout:
 * only a parent can unlink the device.
 */
@Composable
fun ChildHome(
    sos: SosUiState,
    onSos: () -> Unit,
    viewModel: ChildHomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    // Requests location/notification permissions and starts background services
    val setup = rememberProtectionSetup()
    var showSetup by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Contact?>(null) }

    // One tap calls right away; without the permission the dialer opens instead
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingCall?.let { call(context, it, direct = granted) }
        pendingCall = null
    }
    fun dial(phone: String) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) call(context, phone, direct = true)
        else { pendingCall = phone; callPermission.launch(Manifest.permission.CALL_PHONE) }
    }

    val profile = state.profile
    val labels = state.contacts.associate { it.id to it.label }
    val markers = state.positions.map { p ->
        val self = p.userId == profile?.id
        MapMarker(
            id = p.userId,
            latitude = p.latitude,
            longitude = p.longitude,
            title = if (self) "Я" else labels[p.userId] ?: p.name,
            subtitle = if (p.paused) "на паузе · ${timeAgo(p.recordedAt)}" else timeAgo(p.recordedAt),
            isSelf = self,
            paused = p.paused
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TopBar(profile, onProfile = { showProfile = true })

        if (setup.value.done < 4) {
            val fg = LocalFgColors.current
            FgCard(onClick = { showSetup = true }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(fg.warnSoft, fg.warn, "!")
                    Text("Настройте защиту: ${setup.value.done} из 4", fontWeight = FontWeight.Bold)
                }
            }
        }

        FamilyMap(
            markers = markers,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SosButton(state = sos, size = 150.dp, onPress = onSos)
            SosStatusText(sos)
        }

        if (state.contacts.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.contacts, key = { it.id }) { contact ->
                    ContactChip(
                        contact = contact,
                        onCall = { contact.phoneNumber?.takeIf { it.isNotBlank() }?.let { dial(it) } },
                        onRename = { renaming = contact }
                    )
                }
            }
            Text(
                "Нажмите, чтобы позвонить. Удерживайте, чтобы переименовать.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showSetup) {
        ProtectionSetupDialog(
            status = setup.value,
            onChanged = { setup.value = readProtectionStatus(context) },
            onDismiss = { showSetup = false }
        )
    }
    if (showProfile && profile != null) {
        ProfileDialog(
            profile = profile,
            onPickAvatar = viewModel::uploadAvatar,
            onSetup = { showProfile = false; showSetup = true },
            onDismiss = { showProfile = false }
        )
    }
    renaming?.let { contact ->
        RenameDialog(
            contact = contact,
            onSave = { viewModel.renameContact(contact, it); renaming = null },
            onDismiss = { renaming = null }
        )
    }
    state.message?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("Понятно") } }
        )
    }
}

private fun call(context: android.content.Context, phone: String, direct: Boolean) {
    val action = if (direct) Intent.ACTION_CALL else Intent.ACTION_DIAL
    runCatching { context.startActivity(Intent(action, Uri.parse("tel:$phone"))) }
}

@Composable
private fun TopBar(profile: UserProfile?, onProfile: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onProfile)
                .padding(4.dp)
        ) {
            Avatar(profile?.firstName.orEmpty(), size = 44.dp, imageUrl = profile?.avatar)
            Column {
                Text(
                    profile?.firstName?.let { "Привет, $it" } ?: "Привет!",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text("Мой профиль", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        ThemeToggle()
    }
}

/** Light → dark → as in the system, one tap each. */
@Composable
private fun ThemeToggle() {
    val (next, icon) = when (ThemePrefs.mode) {
        ThemeMode.LIGHT -> ThemeMode.DARK to "☀️"
        ThemeMode.DARK -> ThemeMode.SYSTEM to "🌙"
        ThemeMode.SYSTEM -> ThemeMode.LIGHT to "🌓"
    }
    IconButton(onClick = { ThemePrefs.set(next) }) {
        Text(icon, fontSize = 22.sp)
    }
}

@Composable
private fun ContactChip(contact: Contact, onCall: () -> Unit, onRename: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(contact.id) {
                detectTapGestures(onTap = { onCall() }, onLongPress = { onRename() })
            }
            .padding(vertical = 6.dp)
    ) {
        Avatar(contact.firstName, size = 52.dp, imageUrl = contact.avatar)
        Text(
            contact.label,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RenameDialog(contact: Contact, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(contact.label) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Как подписать?") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it.take(40) }, singleLine = true)
                Text(
                    "Оставьте пустым, чтобы вернуть подпись по умолчанию.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private val shownDate = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Days until the next birthday; 0 on the day itself. */
internal fun daysToBirthday(birth: LocalDate, today: LocalDate = LocalDate.now()): Long {
    var next = birth.withYear(today.year)  // 29 Feb becomes 28 Feb in other years
    if (next.isBefore(today)) next = birth.withYear(today.year + 1)
    return ChronoUnit.DAYS.between(today, next)
}

@Composable
private fun ProfileDialog(
    profile: UserProfile,
    onPickAvatar: (Uri) -> Unit,
    onSetup: () -> Unit,
    onDismiss: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPickAvatar)
    }
    val birth = profile.birthDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Мой профиль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(profile.firstName, size = 64.dp, imageUrl = profile.avatar)
                    TextButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Сменить фото") }
                }
                val fullName = listOf(profile.lastName, profile.firstName).filter { it.isNotBlank() }.joinToString(" ")
                if (fullName.isNotBlank()) ProfileRow("ФИО", fullName)
                profile.username?.let { ProfileRow("Имя пользователя", it) }
                birth?.let {
                    val days = daysToBirthday(it)
                    ProfileRow(
                        "Дата рождения",
                        it.format(shownDate) + if (days == 0L) " · сегодня день рождения! 🎉" else " · до дня рождения $days дн."
                    )
                }
                Text("Оформление", fontWeight = FontWeight.Bold)
                ThemeSwitcher()
                TextButton(onClick = onSetup, contentPadding = PaddingValues(0.dp)) { Text("Настройка защиты") }
                Text(
                    "Родители видят, где ты, какими приложениями пользуешься, какие сайты заблокированы и твои сигналы SOS. Переписку и фото они не видят. Данные профиля меняет родитель.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
