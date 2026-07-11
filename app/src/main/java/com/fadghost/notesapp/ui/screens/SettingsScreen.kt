package com.fadghost.notesapp.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fadghost.notesapp.data.backup.ImportMode
import com.fadghost.notesapp.data.prefs.ThemeMode
import com.fadghost.notesapp.ui.settings.BackupViewModel
import com.fadghost.notesapp.ui.theme.Aura
import com.fadghost.notesapp.ui.theme.AuraType

@Composable
fun SettingsScreen(
    currentMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit
) {
    val tokens = Aura.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        BasicHeader("Settings")
        Spacer(Modifier.height(20.dp))

        SectionCard(title = "Appearance") {
            BasicRowLabel("Theme")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeMode.entries.forEach { mode ->
                    ThemeChip(
                        label = mode.pretty(),
                        selected = mode == currentMode,
                        onClick = { onSelectMode(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        BackupSection()

        Spacer(Modifier.height(16.dp))
        com.fadghost.notesapp.ui.settings.DiarySettingsSection()

        Spacer(Modifier.height(16.dp))
        com.fadghost.notesapp.ui.settings.AiSettingsSection()

        Spacer(Modifier.height(96.dp)) // clear the floating nav bar
    }
}

@Composable
private fun BackupSection(viewModel: BackupViewModel = hiltViewModel()) {
    val tokens = Aura.tokens
    val status by viewModel.status.collectAsState()
    val pending by viewModel.pendingPreview.collectAsState()
    val busy by viewModel.busy.collectAsState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::loadPreview) }

    SectionCard(title = "Backup") {
        ActionRow("Export all notes", "ZIP: markdown + metadata + checksums") {
            if (!busy) exportLauncher.launch("notesapp-backup.zip")
        }
        DividerLine()
        ActionRow("Import from ZIP", "Preview, then replace or merge") {
            if (!busy) importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        }
        pending?.let { preview ->
            DividerLine()
            BasicText(
                text = "Ready to import ${preview.manifest.noteCount} notes" +
                    if (preview.isIntact) "" else " (checksum warnings)",
                style = AuraType.label.copy(color = tokens.colors.textSecondary),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeChip("Merge", selected = false, onClick = { viewModel.confirmImport(ImportMode.MERGE) }, modifier = Modifier.weight(1f))
                ThemeChip("Replace", selected = false, onClick = { viewModel.confirmImport(ImportMode.REPLACE) }, modifier = Modifier.weight(1f))
                ThemeChip("Cancel", selected = false, onClick = { viewModel.cancelImport() }, modifier = Modifier.weight(1f))
            }
        }
        status?.let {
            DividerLine()
            BasicText(it, style = AuraType.label.copy(color = tokens.colors.textSecondary), modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    val tokens = Aura.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radii.sm))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = AuraType.body.copy(color = tokens.colors.textPrimary))
            BasicText(subtitle, style = AuraType.label.copy(color = tokens.colors.textSecondary))
        }
    }
}

private fun ThemeMode.pretty(): String = when (this) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System"
}

@Composable
private fun BasicHeader(text: String) {
    val tokens = Aura.tokens
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = AuraType.title.copy(color = tokens.colors.textPrimary)
    )
}

@Composable
private fun BasicRowLabel(text: String) {
    val tokens = Aura.tokens
    androidx.compose.foundation.text.BasicText(
        text = text,
        style = AuraType.body.copy(color = tokens.colors.textPrimary)
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val tokens = Aura.tokens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.radii.md))
            .background(tokens.colors.surface)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.md))
            .padding(16.dp)
    ) {
        androidx.compose.foundation.text.BasicText(
            text = title,
            style = AuraType.label.copy(color = tokens.colors.textSecondary)
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = Aura.tokens
    val t by animateFloatAsState(
        if (selected) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "chip"
    )
    val bg = lerp(tokens.colors.surface, tokens.colors.accent.copy(alpha = 0.9f), t)
    val fg = lerp(tokens.colors.textSecondary, tokens.colors.background, t)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(tokens.radii.pill))
            .background(bg)
            .border(1.dp, tokens.colors.outline, RoundedCornerShape(tokens.radii.pill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = AuraType.label.copy(color = fg, textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun PlaceholderRow(title: String, subtitle: String) {
    val tokens = Aura.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicText(
                text = title,
                style = AuraType.body.copy(color = tokens.colors.textPrimary)
            )
            androidx.compose.foundation.text.BasicText(
                text = subtitle,
                style = AuraType.label.copy(color = tokens.colors.textSecondary)
            )
        }
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tokens.colors.textSecondary.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun DividerLine() {
    val tokens = Aura.tokens
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(tokens.colors.outline)
    )
}
