package com.fadghost.notesapp.ui.calendar

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fadghost.notesapp.data.db.entity.Recurrence
import com.fadghost.notesapp.ui.components.AuraGlyph
import com.fadghost.notesapp.ui.components.Glyph
import com.fadghost.notesapp.ui.theme.Aura
import com.fadghost.notesapp.ui.theme.AuraType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Calendar tab (PLAN.md §8). Month view with springy swipe + dots, a week strip,
 * a selected-day panel, and an agenda list with sticky day headers — all Aura
 * tokens, no Material. Hosts natural-language quick-add, the create/edit sheet,
 * the notification-permission flow, and the battery-optimisation warning.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val tokens = Aura.tokens
    val zone = remember { ZoneId.systemDefault() }
    val data by viewModel.data.collectAsState()
    val context = LocalContext.current
    val deepLinkReminderId by CalendarDeepLink.pendingReminderId.collectAsState()

    val today = remember { LocalDate.now(zone) }
    var selectedEpochDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpochDay)
    var visibleMonth by remember { mutableStateOf(YearMonth.from(selected)) }

    var draft by remember { mutableStateOf<ItemDraft?>(null) }

    // Deep-link from a reminder notification → open its edit sheet.
    LaunchedEffect(deepLinkReminderId, data.reminders) {
        val id = deepLinkReminderId ?: return@LaunchedEffect
        data.reminders.firstOrNull { it.id == id }?.let { r ->
            draft = ItemDraft(
                baseId = r.id, kind = CalendarKind.REMINDER, title = r.title,
                start = r.triggerAt, end = r.triggerAt, notes = "", recurrence = r.recurrence
            )
            CalendarDeepLink.clear()
        }
    }

    // Occurrence expansion window covers the visible grid and ~4 months of agenda.
    val gridStart = visibleMonth.atDay(1).minusDays(7)
    val windowStartMs = remember(gridStart) {
        minOf(gridStart, today).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val windowEndMs = remember(visibleMonth) {
        maxOf(today.plusDays(120), visibleMonth.atEndOfMonth().plusDays(14))
            .atStartOfDay(zone).toInstant().toEpochMilli()
    }
    val byDay = remember(data, visibleMonth) {
        CalendarExpand.groupByDay(
            CalendarExpand.itemsInRange(data.events, data.reminders, zone, windowStartMs, windowEndMs),
            zone
        )
    }
    val agendaDays = remember(byDay) { byDay.keys.filter { !it.isBefore(today) }.sorted() }
    val hasAnything = data.events.isNotEmpty() || data.reminders.isNotEmpty()

    fun openNew() {
        val start = selected.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        draft = ItemDraft(kind = CalendarKind.EVENT, start = start, end = start + 3_600_000L)
    }

    fun editItem(item: CalendarItem) {
        draft = if (item.kind == CalendarKind.EVENT) {
            data.events.firstOrNull { it.id == item.baseId }?.let { e ->
                ItemDraft(e.id, CalendarKind.EVENT, e.title, e.startAt, e.endAt, e.notes ?: "", e.recurrence)
            }
        } else {
            data.reminders.firstOrNull { it.id == item.baseId }?.let { r ->
                ItemDraft(r.id, CalendarKind.REMINDER, r.title, r.triggerAt, r.triggerAt, "", r.recurrence)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(tokens.colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp
            )
        ) {
            item(key = "banners") {
                NotificationPermissionBanner()
                BatteryBanner(context)
            }
            item(key = "quickadd") {
                QuickAddBar(
                    zone = zone,
                    onConfirm = { r ->
                        val at = r.dateTime.atZone(zone).toInstant().toEpochMilli()
                        viewModel.saveReminder(0L, r.title, at, zone.id, r.recurrence)
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            item(key = "month") {
                MonthSection(
                    month = visibleMonth,
                    selected = selected,
                    today = today,
                    dotsByDay = byDay,
                    onMonthDelta = { visibleMonth = visibleMonth.plusMonths(it.toLong()) },
                    onSelect = { selectedEpochDay = it.toEpochDay(); visibleMonth = YearMonth.from(it) },
                    onJumpToday = { selectedEpochDay = today.toEpochDay(); visibleMonth = YearMonth.from(today) }
                )
            }
            item(key = "weekstrip") {
                WeekStrip(
                    selected = selected,
                    today = today,
                    byDay = byDay,
                    onSelect = { selectedEpochDay = it.toEpochDay(); visibleMonth = YearMonth.from(it) },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            item(key = "selectedday") {
                SelectedDayPanel(
                    date = selected,
                    today = today,
                    items = byDay[selected].orEmpty(),
                    zone = zone,
                    onAdd = ::openNew,
                    onEdit = ::editItem,
                    onToggleDone = { viewModel.setReminderDone(it.baseId, !it.done) }
                )
            }

            if (agendaDays.isEmpty() && !hasAnything) {
                item(key = "empty") { CalendarEmptyState() }
            } else {
                item(key = "agendatitle") {
                    BasicText(
                        "Agenda",
                        style = AuraType.title.copy(color = tokens.colors.textPrimary),
                        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
                    )
                }
                agendaDays.forEach { day ->
                    stickyHeader(key = "h_${day.toEpochDay()}") {
                        DayHeader(day, today)
                    }
                    items(byDay[day].orEmpty(), key = { "${it.kind}_${it.baseId}_${it.startMillis}" }) { item ->
                        AgendaRow(
                            item = item, zone = zone,
                            onClick = { editItem(item) },
                            onToggleDone = { viewModel.setReminderDone(item.baseId, !item.done) }
                        )
                    }
                }
            }
        }

        // Floating add button (bottom-end, above the nav pill).
        AddButton(
            onClick = ::openNew,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp)
        )

        ItemDetailSheet(
            draft = draft,
            zone = zone,
            onDismiss = { draft = null },
            onSave = { d ->
                if (d.kind == CalendarKind.EVENT) {
                    viewModel.saveEvent(d.baseId, d.title, d.start, d.end, zone.id, d.notes, d.recurrence)
                } else {
                    viewModel.saveReminder(d.baseId, d.title, d.start, zone.id, d.recurrence)
                }
            },
            onDelete = { d ->
                if (d.kind == CalendarKind.EVENT) viewModel.deleteEvent(d.baseId)
                else viewModel.deleteReminder(d.baseId)
            }
        )
    }
}

// --- Week strip -------------------------------------------------------------

@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    byDay: Map<LocalDate, List<CalendarItem>>,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = Aura.tokens
    val monday = selected.minusDays(((selected.dayOfWeek.value - 1).toLong()))
    val week = (0L..6L).map { monday.plusDays(it) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radii.md))
            .background(tokens.colors.surface)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.md))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        week.forEach { d ->
            val isSel = d == selected
            val hasItems = byDay[d].orEmpty().isNotEmpty()
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(tokens.radii.sm))
                    .background(if (isSel) tokens.colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = { onSelect(d) })
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BasicText(
                    d.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                    style = AuraType.label.copy(color = if (isSel) tokens.colors.background else tokens.colors.textSecondary)
                )
                Spacer(Modifier.size(4.dp))
                BasicText(
                    d.dayOfMonth.toString(),
                    style = AuraType.body.copy(
                        color = when {
                            isSel -> tokens.colors.background
                            d == today -> tokens.colors.accent
                            else -> tokens.colors.textPrimary
                        }
                    )
                )
                Spacer(Modifier.size(3.dp))
                Box(
                    Modifier.size(4.dp).clip(CircleShape).background(
                        if (hasItems) (if (isSel) tokens.colors.background else tokens.colors.accent)
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                )
            }
        }
    }
}

// --- Selected-day panel -----------------------------------------------------

@Composable
private fun SelectedDayPanel(
    date: LocalDate,
    today: LocalDate,
    items: List<CalendarItem>,
    zone: ZoneId,
    onAdd: () -> Unit,
    onEdit: (CalendarItem) -> Unit,
    onToggleDone: (CalendarItem) -> Unit
) {
    val tokens = Aura.tokens
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(dayLabel(date, today), style = AuraType.title.copy(color = tokens.colors.textPrimary))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(tokens.radii.pill))
                    .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.pill))
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onAdd)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                BasicText("+ Add", style = AuraType.label.copy(color = tokens.colors.accent))
            }
        }
        Spacer(Modifier.size(8.dp))
        if (items.isEmpty()) {
            BasicText(
                "Nothing scheduled.",
                style = AuraType.body.copy(color = tokens.colors.textSecondary),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            items.forEach { item ->
                AgendaRow(item, zone, onClick = { onEdit(item) }, onToggleDone = { onToggleDone(item) })
            }
        }
    }
}

// --- Agenda -----------------------------------------------------------------

@Composable
private fun DayHeader(day: LocalDate, today: LocalDate) {
    val tokens = Aura.tokens
    Box(
        Modifier
            .fillMaxWidth()
            .background(tokens.colors.background)
            .padding(vertical = 8.dp)
    ) {
        BasicText(dayLabel(day, today), style = AuraType.label.copy(color = tokens.colors.textSecondary))
    }
}

@Composable
private fun AgendaRow(
    item: CalendarItem,
    zone: ZoneId,
    onClick: () -> Unit,
    onToggleDone: () -> Unit
) {
    val tokens = Aura.tokens
    val accent = if (item.kind == CalendarKind.EVENT) tokens.colors.accent else tokens.colors.danger
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(tokens.radii.md))
            .background(tokens.colors.surface)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.md))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                item.title,
                style = AuraType.body.copy(
                    color = if (item.done) tokens.colors.textSecondary else tokens.colors.textPrimary,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                )
            )
            BasicText(timeLabel(item, zone), style = AuraType.label.copy(color = tokens.colors.textSecondary))
        }
        if (item.isRecurring) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(tokens.radii.pill))
                    .background(accent.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                BasicText(item.recurrence.shortLabel(), style = AuraType.label.copy(color = accent))
            }
        }
        if (item.kind == CalendarKind.REMINDER) {
            Spacer(Modifier.size(10.dp))
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, if (item.done) accent else tokens.colors.outline, CircleShape)
                    .background(if (item.done) accent else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onToggleDone),
                contentAlignment = Alignment.Center
            ) {
                if (item.done) AuraGlyph(Glyph.CHECK, tokens.colors.background, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = Aura.tokens
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(tokens.colors.accent)
            .border(1.dp, tokens.colors.outline, CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AuraGlyph(Glyph.PLUS, tokens.colors.background, Modifier.size(26.dp))
    }
}

@Composable
private fun CalendarEmptyState() {
    val tokens = Aura.tokens
    Column(
        Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(tokens.colors.accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { AuraGlyph(Glyph.CALENDAR, tokens.colors.accent, Modifier.size(30.dp)) }
        Spacer(Modifier.size(14.dp))
        BasicText("Nothing planned yet", style = AuraType.title.copy(color = tokens.colors.textPrimary))
        Spacer(Modifier.size(6.dp))
        BasicText(
            "Add an event or reminder — or type \"gym tomorrow 7am\" above.",
            style = AuraType.body.copy(color = tokens.colors.textSecondary, textAlign = TextAlign.Center)
        )
    }
}

// --- Permission + battery banners ------------------------------------------

@Composable
private fun NotificationPermissionBanner() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasNotifPermission(context)) }
    var asked by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        asked = true
    }
    if (granted || dismissed) return

    val tokens = Aura.tokens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(tokens.radii.md))
            .background(tokens.colors.surface)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.md))
            .padding(16.dp)
    ) {
        BasicText("Turn on reminder notifications", style = AuraType.body.copy(color = tokens.colors.textPrimary))
        Spacer(Modifier.size(4.dp))
        BasicText(
            if (asked) "Notifications are off. Enable them in Settings so reminders can alert you."
            else "Reminders need notification access to alert you at the right time.",
            style = AuraType.label.copy(color = tokens.colors.textSecondary)
        )
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BannerAction(if (asked) "Open Settings" else "Enable") {
                if (asked) openAppNotificationSettings(context) else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Spacer(Modifier.size(8.dp))
            BannerAction("Not now", subtle = true) { dismissed = true }
        }
    }
}

@Composable
private fun BatteryBanner(context: Context) {
    var dismissed by remember { mutableStateOf(false) }
    val ignoring = remember { isIgnoringBatteryOptimizations(context) }
    if (ignoring || dismissed) return

    val tokens = Aura.tokens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(tokens.radii.md))
            .background(tokens.colors.danger.copy(alpha = 0.10f))
            .border(1.dp, tokens.colors.danger.copy(alpha = 0.4f), RoundedCornerShape(tokens.radii.md))
            .padding(16.dp)
    ) {
        BasicText("Reminders may be delayed", style = AuraType.body.copy(color = tokens.colors.textPrimary))
        Spacer(Modifier.size(4.dp))
        BasicText(
            "Battery optimisation is on for this app. Allow it to run unrestricted so alarms fire on time.",
            style = AuraType.label.copy(color = tokens.colors.textSecondary)
        )
        Spacer(Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BannerAction("Fix") { openBatterySettings(context) }
            Spacer(Modifier.size(8.dp))
            BannerAction("Dismiss", subtle = true) { dismissed = true }
        }
    }
}

@Composable
private fun BannerAction(label: String, subtle: Boolean = false, onClick: () -> Unit) {
    val tokens = Aura.tokens
    val color = if (subtle) tokens.colors.textSecondary else tokens.colors.accent
    BasicText(
        label,
        style = AuraType.label.copy(color = color),
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.radii.pill))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

// --- Formatting + platform helpers -----------------------------------------

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun timeLabel(item: CalendarItem, zone: ZoneId): String {
    val start = Instant.ofEpochMilli(item.startMillis).atZone(zone).toLocalTime().format(TIME_FMT)
    return if (item.kind == CalendarKind.EVENT && item.endMillis != null) {
        val end = Instant.ofEpochMilli(item.endMillis).atZone(zone).toLocalTime().format(TIME_FMT)
        "$start – $end"
    } else start
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}

private fun hasNotifPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openBatterySettings(context: Context) {
    // Deep-link to the OS exemption screen (PLAN.md §8).
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
}
