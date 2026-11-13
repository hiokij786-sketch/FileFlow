package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileCategory
import com.example.data.model.FileItem
import com.example.data.model.PdfMetadataInfo
import com.example.data.model.ProcessStep
import com.example.data.model.ToolStatusState
import com.example.data.model.formatFileSize
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.FileFlowToolTopBar
import com.example.ui.components.OutputFileActionsCard
import com.example.ui.components.ToolProgressAndResultCard
import com.example.ui.components.WorkspaceOrDeviceFilePickerSheet
import com.example.ui.theme.MonoMetricsStyle
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PdfProtectScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenViewer: (FileItem) -> Unit,
    onRequestExport: (FileItem) -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var selectedPdf by remember { mutableStateOf<FileItem?>(null) }
    var isUnlockMode by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var authorizedChecked by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(ToolStatusState<FileItem>()) }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "PDF Security",
                subtitle = "Protect with password or authorized unlock",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isUnlockMode,
                        onClick = { isUnlockMode = false; status = ToolStatusState() },
                        label = { Text("Add Password") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) }
                    )
                    FilterChip(
                        selected = isUnlockMode,
                        onClick = { isUnlockMode = true; status = ToolStatusState() },
                        label = { Text("Authorized Unlock") },
                        leadingIcon = { Icon(Icons.Filled.LockOpen, contentDescription = null) }
                    )
                }
            }

            item {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedPdf?.name ?: "Select PDF Document")
                }
            }

            if (selectedPdf == null) {
                item {
                    EmptyStatePanel(
                        icon = if (isUnlockMode) Icons.Filled.LockOpen else Icons.Filled.Lock,
                        title = if (isUnlockMode) "Unlock Authorized PDF" else "Encrypt PDF with Password",
                        description = if (isUnlockMode) {
                            "Remove password protection only when you supply the valid document password and confirm authorization."
                        } else {
                            "Protect sensitive documents with 128-bit encryption locally on your device."
                        },
                        actionLabel = "Select PDF",
                        onAction = { showPicker = true }
                    )
                }
            } else {
                val pdf = selectedPdf!!
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(if (isUnlockMode) "Current PDF Password" else "New Password (min 4 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!isUnlockMode) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = authorizedChecked,
                                onCheckedChange = { authorizedChecked = it }
                            )
                            Text(
                                text = "I confirm I own or have explicit authorization to remove protection from this PDF.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            scope.launch {
                                if (!isUnlockMode && password != confirmPassword) {
                                    status = ToolStatusState(ProcessStep.ERROR, errorMessage = "Passwords do not match.")
                                    return@launch
                                }
                                if (isUnlockMode && !authorizedChecked) {
                                    status = ToolStatusState(ProcessStep.ERROR, errorMessage = "Please confirm authorization first.")
                                    return@launch
                                }
                                status = ToolStatusState(ProcessStep.PROCESSING, 0.35f, if (isUnlockMode) "Verifying password..." else "Encrypting PDF...")
                                val base = File(pdf.path).nameWithoutExtension
                                val suffix = if (isUnlockMode) "unlocked" else "protected"
                                val outFile = viewModel.workspaceRepo.buildUniqueOutputFile(prefs.defaultOutputFolder, "${base}_$suffix.pdf")

                                val res = if (isUnlockMode) {
                                    viewModel.pdfEngine.removePdfProtection(File(pdf.path), outFile, password)
                                } else {
                                    viewModel.pdfEngine.protectPdf(File(pdf.path), outFile, password, password)
                                }

                                res.onSuccess { saved ->
                                    val item = viewModel.workspaceRepo.recordProcessedFile(
                                        saved,
                                        if (isUnlockMode) "Unlocked PDF" else "Protected PDF"
                                    )
                                    status = ToolStatusState(ProcessStep.SUCCESS, 1f, "Done", result = item)
                                }.onFailure { err ->
                                    outFile.delete()
                                    status = ToolStatusState(ProcessStep.ERROR, errorMessage = err.message)
                                }
                            }
                        },
                        enabled = password.isNotBlank() && status.step != ProcessStep.PROCESSING,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isUnlockMode) "Verify & Remove Protection" else "Encrypt & Save PDF")
                    }
                }
            }

            item {
                ToolProgressAndResultCard(
                    state = status,
                    onDismissError = { status = ToolStatusState() }
                ) { outItem ->
                    OutputFileActionsCard(
                        title = if (isUnlockMode) "Protection Removed" else "PDF Encrypted",
                        file = outItem,
                        onPreview = if (!isUnlockMode) null else { { onOpenViewer(outItem) } },
                        onSaveToDevice = { onRequestExport(outItem) },
                        onShare = { viewModel.shareFile(outItem) },
                        onOpenExternal = { viewModel.openExternal(outItem) }
                    )
                }
            }
        }
    }

    if (showPicker) {
        WorkspaceOrDeviceFilePickerSheet(
            title = "Select PDF Document",
            workspaceFiles = allFiles,
            allowedCategories = setOf(FileCategory.PDF),
            mimeFilter = arrayOf("application/pdf"),
            allowMultiple = false,
            onImportUris = { uris -> viewModel.importUris(uris, "PDFs") },
            onFilesPicked = { picked -> selectedPdf = picked.firstOrNull() },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
fun PdfInfoScreen(
    initialFilePath: String?,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    var selectedPdf by remember(initialFilePath, allFiles) {
        mutableStateOf(
            initialFilePath?.let { path ->
                allFiles.find { it.path == path } ?: File(path).takeIf { it.exists() }?.let { f ->
                    FileItem(
                        id = f.absolutePath,
                        name = f.name,
                        path = f.absolutePath,
                        sizeBytes = f.length(),
                        lastModified = f.lastModified(),
                        mimeType = "application/pdf",
                        category = FileCategory.PDF,
                        folderName = f.parentFile?.name ?: "Files"
                    )
                }
            }
        )
    }
    var info by remember { mutableStateOf<PdfMetadataInfo?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var passwordPrompt by remember { mutableStateOf("") }
    var needsPassword by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadInfo(file: File, pwd: String? = null) {
        scope.launch {
            errorMsg = null
            viewModel.pdfEngine.getPdfInfo(file, pwd).onSuccess {
                info = it
                needsPassword = false
            }.onFailure { err ->
                info = null
                if (err.message?.contains("password", ignoreCase = true) == true) {
                    needsPassword = true
                }
                errorMsg = err.message
            }
        }
    }

    LaunchedEffect(selectedPdf) {
        selectedPdf?.let { loadInfo(File(it.path)) }
    }

    Scaffold(
        topBar = {
            FileFlowToolTopBar(
                title = "PDF Information",
                subtitle = "Metadata, version, dates & properties",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedPdf?.name ?: "Select PDF Document")
                }
            }

            if (selectedPdf == null) {
                item {
                    EmptyStatePanel(
                        icon = Icons.Filled.Info,
                        title = "Inspect PDF Metadata",
                        description = "View page count, file size, creation & modification dates, author, producer, and PDF version.",
                        actionLabel = "Select PDF",
                        onAction = { showPicker = true }
                    )
                }
            }

            if (needsPassword && selectedPdf != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Password Protected PDF", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = passwordPrompt,
                                onValueChange = { passwordPrompt = it },
                                label = { Text("Enter Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { loadInfo(File(selectedPdf!!.path), passwordPrompt) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Unlock & Inspect")
                            }
                        }
                    }
                }
            }

            errorMsg?.let { err ->
                item {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            info?.let { meta ->
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            InfoRow("Filename", meta.fileName)
                            InfoRow("File Size", formatFileSize(meta.fileSizeBytes))
                            InfoRow("Pages", "${meta.pageCount}")
                            InfoRow("PDF Version", meta.pdfVersion)
                            InfoRow("Encrypted", if (meta.isEncrypted) "Yes (Password Protected)" else "No")
                            InfoRow("Title", meta.title ?: "Not specified")
                            InfoRow("Author", meta.author ?: "Not specified")
                            InfoRow("Subject", meta.subject ?: "Not specified")
                            InfoRow("Creator", meta.creator ?: "Not specified")
                            InfoRow("Producer", meta.producer ?: "Not specified")
                            InfoRow("Created", meta.creationDate ?: "Unavailable")
                            InfoRow("Modified", meta.modificationDate ?: "Unavailable", showDivider = false)
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        WorkspaceOrDeviceFilePickerSheet(
            title = "Select PDF to Inspect",
            workspaceFiles = allFiles,
            allowedCategories = setOf(FileCategory.PDF),
            mimeFilter = arrayOf("application/pdf"),
            allowMultiple = false,
            onImportUris = { uris -> viewModel.importUris(uris, "PDFs") },
            onFilesPicked = { picked -> selectedPdf = picked.firstOrNull() },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, showDivider: Boolean = true) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.4f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = MonoMetricsStyle,
                modifier = Modifier.weight(0.6f)
            )
        }
        if (showDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
