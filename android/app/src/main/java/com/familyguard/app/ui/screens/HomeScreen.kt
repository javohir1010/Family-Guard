package com.familyguard.app.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.familyguard.app.data.model.AppNotification
import com.familyguard.app.data.model.MemberPosition
import com.familyguard.app.services.LocationForegroundService
import com.familyguard.app.ui.theme.LocalFgColors
import com.familyguard.app.viewmodel.AuthViewModel
import com.familyguard.app.viewmodel.HomeViewModel
import com.familyguard.app.viewmodel.SosViewModel

/**
 * Home screen after login: the child gets the SOS screen, the parent an
 * overview of the family. [sosRequests] grows each time the home-screen
 * widget is tapped; every new value counts as one SOS press.
 */
@Composable
fun HomeScreen(
    sosRequests: Int,
    homeViewModel: HomeViewModel = hiltViewModel(),
    sosViewModel: SosViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val home by homeViewModel.uiState.collectAsState()
    val sos by sosViewModel.uiState.collectAsState()

    // Remember which widget taps were already handled, so a rotation does not resend
    var handledRequests by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(sosRequests) {
        if (sosRequests > handledRequests) {
            handledRequests = sosRequests
            sosViewModel.onSosPressed()
        }
    }

    // Parent's logout progress or error
    LogoutApprovalDialog(authViewModel)

    // Older adult accounts fill in gender, birth date and phone once
    if (!home.profileComplete) {
        CompleteProfileDialog(
            saving = home.profileSaving,
            error = home.profileError,
            onSave = homeViewModel::completeProfile
        )
    }

    when (home.role) {
        null -> Box(Modifier.fillMaxSize())
        // Grandparents get the parent's overview for now, without managing anything
        HomeViewModel.ROLE_PARENT, HomeViewModel.ROLE_GRANDPARENT -> ParentHome(
            name = home.firstName,
            children = home.children,
            events = home.events,
            isRefreshing = home.isRefreshing,
            error = home.error,
            sos = sos,
            onSos = sosViewModel::onSosPressed,
            onRefresh = homeViewModel::refresh,
            onUnlink = if (home.role == HomeViewModel.ROLE_PARENT) homeViewModel::unlinkDevice else null,
            locationPaused = home.locationPaused,
            onLocationPausedChange = homeViewModel::setLocationPaused,
            onLogout = authViewModel::logout
        )
        // No logout for a child: only a parent can unlink the device
        else -> ChildHome(
            sos = sos,
            onSos = sosViewModel::onSosPressed
        )
    }
}

@Composable
private fun ParentHome(
    name: String,
    children: List<MemberPosition>,
    events: List<AppNotification>,
    isRefreshing: Boolean,
    error: String?,
    sos: com.familyguard.app.viewmodel.SosUiState,
    onSos: () -> Unit,
    onRefresh: () -> Unit,
    onUnlink: ((MemberPosition) -> Unit)?,
    locationPaused: Boolean,
    onLocationPausedChange: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val fg = LocalFgColors.current

    // Adults share their position too, so children see them on the family map
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { LocationForegroundService.startService(context) }
    LaunchedEffect(locationPaused) {
        if (locationPaused) return@LaunchedEffect
        if (LocationForegroundService.hasLocationPermission(context)) {
            LocationForegroundService.startService(context)
        } else {
            locationPermission.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }
    var unlinkTarget by remember { mutableStateOf<MemberPosition?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Header(
            title = "Моя семья",
            subtitle = if (name.isNotBlank()) "Здравствуйте, $name" else "Обзор за сегодня",
            letter = name
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Дети")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                Text(if (isRefreshing) "Обновляем…" else "Обновить")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

        if (children.isEmpty() && !isRefreshing) {
            FgCard {
                CardTitle("Пока нет данных о детях")
                CardHint("Добавьте ребёнка по коду приглашения в веб-панели и включите защиту на его телефоне. Здесь появятся его местоположение и заряд батареи.")
            }
        }
        children.forEach { child ->
            FgCard(onClick = {
                // Opens the point in any installed maps app
                val uri = Uri.parse("geo:${child.latitude},${child.longitude}?q=${child.latitude},${child.longitude}(${Uri.encode(child.name)})")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(child.name)
                    Column(Modifier.weight(1f)) {
                        CardTitle(child.name)
                        CardHint(
                            (if (child.isOnline) "В сети · " else "") +
                                "обновлено ${timeAgo(child.recordedAt)} · показать на карте"
                        )
                    }
                    child.batteryLevel?.let { Pill("$it%", warn = it < 25) }
                }
                if (onUnlink != null) {
                    TextButton(onClick = { unlinkTarget = child }) {
                        Text("Отвязать устройство", fontSize = 13.sp)
                    }
                }
            }
        }

        FgCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CardTitle("Моё местоположение")
                    CardHint(
                        if (locationPaused) "На паузе: семья видит место, где вы были последний раз"
                        else "Семья видит, где вы сейчас"
                    )
                }
                Switch(checked = !locationPaused, onCheckedChange = { onLocationPausedChange(!it) })
            }
        }

        SectionTitle("События")
        if (events.isEmpty()) {
            CardHint("Новых событий нет. Здесь появятся SOS, выход из геозон и блокировки.")
        } else {
            FgCard {
                events.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when (e.type) {
                            "sos" -> IconBadge(fg.sosSoft, fg.sos, "!")
                            "geofence", "location" -> IconBadge(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "•")
                            else -> IconBadge(fg.warnSoft, fg.warn, "i")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                e.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = if (e.isRead) FontWeight.Medium else FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            CardHint("${timeAgo(e.sentAt)} · ${e.body}", maxLines = 1)
                        }
                    }
                }
            }
        }

        SectionTitle("Тревога")
        FgCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SosButton(state = sos, size = 96.dp, onPress = onSos)
                Column(Modifier.weight(1f)) {
                    CardTitle("SOS для всей семьи")
                    CardHint("Сигнал с вашим местоположением получат остальные родители")
                }
            }
            SosStatusText(sos)
        }

        SettingsCard(onLogout)
    }

    unlinkTarget?.let { child ->
        AlertDialog(
            onDismissRequest = { unlinkTarget = null },
            title = { Text("Отвязать устройство?") },
            text = { Text("${child.name} выйдет из аккаунта на своём телефоне, но останется в семье. Войти снова можно с любого телефона.") },
            confirmButton = {
                TextButton(onClick = { onUnlink?.invoke(child); unlinkTarget = null }) { Text("Отвязать") }
            },
            dismissButton = {
                TextButton(onClick = { unlinkTarget = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun CompleteProfileDialog(saving: Boolean, error: String?, onSave: (PersonData) -> Unit) {
    var person by remember { mutableStateOf(PersonData()) }
    AlertDialog(
        onDismissRequest = {},  // required once: contacts and quick dial depend on it
        title = { Text("Заполните профиль") },
        text = {
            Column {
                Text(
                    "Это нужно, чтобы дети видели вас в контактах как «Мама», «Папа», «Бабушка» и могли позвонить в одно нажатие.",
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(12.dp))
                PersonFields(person) { person = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(person) }, enabled = person.isComplete && !saving) {
                Text(if (saving) "Сохраняем…" else "Сохранить")
            }
        }
    )
}

@Composable
private fun Header(title: String, subtitle: String, letter: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        Avatar(letter)
    }
}

@Composable
private fun SettingsCard(onLogout: (() -> Unit)?) {
    FgCard {
        CardTitle("Оформление")
        ThemeSwitcher()
        onLogout?.let { LogoutButton(onClick = it, modifier = Modifier.align(Alignment.Start)) }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun CardHint(text: String, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
internal fun IconBadge(bg: Color, fg: Color, symbol: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .background(bg, RoundedCornerShape(14.dp))
    ) {
        Text(symbol, color = fg, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}
