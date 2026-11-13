package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CompressionLevel
import com.example.data.model.ImageFormatOption
import com.example.data.model.SortField
import com.example.data.model.SortOrder
import com.example.data.model.ThemeMode
import com.example.data.model.formatFileSize
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.FileFlowLogoMark
import com.example.ui.theme.GoldPrimary
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenStorage: () -> Unit
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val storage by viewModel.storageOverview.collectAsStateWithLifecycle()

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showProInfoDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showClearTempConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Appearance, processing defaults, storage & privacy",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Theme Preference
        item {
            SettingsSectionCard(title = "Appearance") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = GoldPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text("App Theme", style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.label.removeSuffix(" Mode").removeSuffix(" Default")) }
                        )
                    }
                }
            }
        }

        // Processing Defaults
        item {
            SettingsSectionCard(title = "Processing Defaults") {
                ListItem(
                    headlineContent = { Text("Default Output Folder") },
                    supportingContent = { Text(prefs.defaultOutputFolder) },
                    leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                    modifier = Modifier.clickable { showFolderDialog = true }
                )
                HorizontalDivider()
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Default Image Format", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImageFormatOption.entries.forEach { fmt ->
                            FilterChip(
                                selected = prefs.defaultImageFormat == fmt,
                                onClick = { viewModel.setDefaultImageFormat(fmt) },
                                label = { Text(fmt.extension.uppercase()) }
                            )
                        }
                    }
                }
                HorizontalDivider()
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text("Default PDF Compression Quality", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompressionLevel.entries.forEach { level ->
                            FilterChip(
                                selected = prefs.defaultPdfQuality == level,
                                onClick = { viewModel.setDefaultPdfQuality(level) },
                                label = { Text(level.label) }
                            )
                        }
                    }
                }
                HorizontalDivider()
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Sort, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Default File Sorting", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SortField.entries.forEach { field ->
                            FilterChip(
                                selected = prefs.sortField == field,
                                onClick = { viewModel.setSortPreferences(field, prefs.sortOrder) },
                                label = { Text(field.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortOrder.entries.forEach { ord ->
                            FilterChip(
                                selected = prefs.sortOrder == ord,
                                onClick = { viewModel.setSortPreferences(prefs.sortField, ord) },
                                label = { Text(ord.label) }
                            )
                        }
                    }
                }
            }
        }

        // Storage Management
        item {
            SettingsSectionCard(title = "Storage") {
                ListItem(
                    headlineContent = { Text("Storage Overview") },
                    supportingContent = {
                        Text(
                            storage?.let {
                                "${formatFileSize(it.workspaceTotalBytes)} used • ${formatFileSize(it.deviceAvailableBytes)} available"
                            } ?: "Inspect categories & large files"
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Storage, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onOpenStorage)
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Clear Temporary Files") },
                    supportingContent = { Text("Cache size: ${formatFileSize(storage?.tempCacheBytes ?: 0L)}") },
                    leadingContent = { Icon(Icons.Filled.CleaningServices, contentDescription = null) },
                    modifier = Modifier.clickable { showClearTempConfirm = true }
                )
            }
        }

        // Privacy, Plan & About
        item {
            SettingsSectionCard(title = "Privacy & Information") {
                ListItem(
                    headlineContent = { Text("Privacy Guarantee") },
                    supportingContent = { Text("100% local processing • No mandatory account") },
                    leadingContent = { Icon(Icons.Filled.PrivacyTip, contentDescription = null) },
                    modifier = Modifier.clickable { showPrivacyDialog = true }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Free Tier & Optional Pro Plan") },
                    supportingContent = { Text("All core PDF, Image, ZIP & Scanner tools included free") },
                    leadingContent = { Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = GoldPrimary) },
                    modifier = Modifier.clickable { showProInfoDialog = true }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("About FILEFLOW") },
                    supportingContent = { Text("Version 1.0.0 • Everything Your Files Need.") },
                    leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    modifier = Modifier.clickable { showAboutDialog = true }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Open-Source Licenses") },
                    supportingContent = { Text("PDFBox-Android, CameraX, Jetpack Compose, Coil, Room") },
                    leadingContent = { Icon(Icons.Filled.Code, contentDescription = null) },
                    modifier = Modifier.clickable { showLicensesDialog = true }
                )
            }
        }
    }

    if (showFolderDialog) {
        val folderNames = (folders.map { it.name } + "Processed").distinct()
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Default Output Folder") },
            text = {
                Column {
                    folderNames.forEach { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setDefaultOutputFolder(folder)
                                    showFolderDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = prefs.defaultOutputFolder == folder, onClick = {
                                viewModel.setDefaultOutputFolder(folder)
                                showFolderDialog = false
                            })
                            Text(folder)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFolderDialog = false }) { Text("Close") } }
        )
    }

    if (showClearTempConfirm) {
        ConfirmDeleteDialog(
            title = "Clear Temporary Files?",
            message = "Deletes cached preview thumbnails and scratch files only. Saved documents remain untouched.",
            confirmLabel = "Clear",
            onConfirm = {
                viewModel.clearTemporaryCache { cleared ->
                    Toast.makeText(context, "Cleared ${formatFileSize(cleared)}", Toast.LENGTH_SHORT).show()
                }
                showClearTempConfirm = false
            },
            onDismiss = { showClearTempConfirm = false }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("FILEFLOW Privacy Promise") },
            text = {
                Text(
                    "• All PDF, image, scanner, and archive operations run strictly locally on your device.\n" +
                        "• Zero hidden document uploads, zero analytics tracking of file names or contents, and no mandatory registration.\n" +
                        "• Camera permission is requested only if you open the Document Scanner.\n" +
                        "• External storage access uses Android's zero-permission Photo Picker and Storage Access Framework (SAF)."
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Got It") } }
        )
    }

    if (showProInfoDialog) {
        AlertDialog(
            onDismissRequest = { showProInfoDialog = false },
            title = { Text("Free Forever Core + Future Pro") },
            text = {
                Text(
                    "Every essential tool in FILEFLOW — PDF Merge, Split, Compress, Viewer, Scanner, Image Studio, File Converter, and ZIP Manager — is completely free and functional without subscriptions.\n\n" +
                        "FILEFLOW is architected cleanly to support optional future one-time Pro enhancements without ever gating basic file utility."
                )
            },
            confirmButton = { TextButton(onClick = { showProInfoDialog = false }) { Text("Close") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FileFlowLogoMark(size = 32.dp, darkBackdrop = true)
                    Spacer(Modifier.width(10.dp))
                    Text("FILEFLOW")
                }
            },
            text = {
                Text("Version 1.0.0\nTagline: Everything Your Files Need.\n\nA fast, privacy-focused, offline-first file and document toolkit built natively with Jetpack Compose.")
            },
            confirmButton = { TextButton(onClick = { showAboutDialog = false }) { Text("Close") } }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open-Source Licenses") },
            text = {
                Text(
                    "• Apache PDFBox-Android (Apache License 2.0)\n" +
                        "• AndroidX Jetpack Compose, Navigation, Room, DataStore & CameraX (Apache License 2.0)\n" +
                        "• Coil Image Loading (Apache License 2.0)\n" +
                        "• Kotlinx Serialization & Coroutines (Apache License 2.0)"
                )
            },
            confirmButton = { TextButton(onClick = { showLicensesDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = GoldPrimary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                content()
            }
        }
    }
}
