package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.builder.ApkPackagingEngine
import com.example.data.BuildOutputFormat
import com.example.data.BuildRecordEntity
import com.example.ui.theme.AmberHighlight
import com.example.ui.theme.ElectricCobalt
import com.example.ui.theme.EmeraldMint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BuildsScreen(
    uiState: StudioUiState,
    onSaveBuildToUri: (BuildRecordEntity, Uri) -> Unit,
    onShareBuild: (BuildRecordEntity) -> Unit,
    onDeleteBuild: (BuildRecordEntity) -> Unit,
    onGoToConfigurator: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingSaveRecord by remember { mutableStateOf<BuildRecordEntity?>(null) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val record = pendingSaveRecord
        if (uri != null && record != null) {
            onSaveBuildToUri(record, uri)
        }
        pendingSaveRecord = null
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("builds_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Generated APK & ZIP Builds",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Save directly to device Downloads or share via Android Share Sheet (0 Watermarks).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (uiState.builds.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = ElectricCobalt,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No Builds Generated Yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Open any project in the Configurator or Studio Dashboard and tap Build APK or Studio ZIP.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onGoToConfigurator,
                            modifier = Modifier.testTag("empty_builds_go_configurator")
                        ) {
                            Text("Configure & Build APK")
                        }
                    }
                }
            }
        } else {
            items(uiState.builds, key = { it.id }) { record ->
                BuildArtifactCard(
                    record = record,
                    onSaveToDevice = {
                        pendingSaveRecord = record
                        saveDocumentLauncher.launch(record.fileName)
                    },
                    onShare = { onShareBuild(record) },
                    onDelete = { onDeleteBuild(record) }
                )
            }
        }
    }
}

@Composable
private fun BuildArtifactCard(
    record: BuildRecordEntity,
    onSaveToDevice: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val isZip = record.outputFormat == BuildOutputFormat.ANDROID_STUDIO_ZIP.name
    val isSigned = record.outputFormat == BuildOutputFormat.SIGNED_APK.name
    val badgeColor = when {
        isSigned -> EmeraldMint
        isZip -> ElectricCobalt
        else -> AmberHighlight
    }
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("build_record_card_${record.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isZip) Icons.Default.Archive else Icons.Default.Android,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${record.packageName} • v${record.versionName} (${record.versionCode})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${ApkPackagingEngine.formatBytes(record.fileSizeBytes)} • Built ${dateFormat.format(Date(record.createdAt))} • No Watermark",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Build",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "SHA-256: ${record.sha256Signature.take(28)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSaveToDevice,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_build_button_${record.id}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save File")
                }

                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_build_button_${record.id}"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share / Export")
                }
            }
        }
    }
}

@Composable
fun BuildConsoleDialog(
    consoleState: BuildConsoleState,
    onDismiss: () -> Unit,
    onSaveCompletedToUri: (BuildRecordEntity, Uri) -> Unit,
    onShareCompleted: (BuildRecordEntity) -> Unit,
    onViewAllBuilds: () -> Unit
) {
    if (!consoleState.isVisible) return

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val rec = consoleState.completedRecord
        if (uri != null && rec != null) {
            onSaveCompletedToUri(rec, uri)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!consoleState.isRunning) onDismiss() },
        icon = {
            Icon(
                imageVector = if (consoleState.completedRecord != null) Icons.Default.CheckCircle else Icons.Default.Terminal,
                contentDescription = null,
                tint = if (consoleState.completedRecord != null) EmeraldMint else ElectricCobalt,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = consoleState.stepTitle.ifBlank { "Building Package..." },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    progress = { consoleState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                )

                Surface(
                    color = Color(0xFF090D16),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        consoleState.logs.forEach { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                        if (consoleState.errorMessage != null) {
                            Text(
                                text = "[Error] ${consoleState.errorMessage}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                consoleState.completedRecord?.let { record ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { saveLauncher.launch(record.fileName) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("console_save_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = { onShareCompleted(record) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("console_share_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!consoleState.isRunning) {
                TextButton(
                    onClick = {
                        onDismiss()
                        onViewAllBuilds()
                    },
                    modifier = Modifier.testTag("console_view_builds_button")
                ) {
                    Text("View All Builds")
                }
            }
        },
        dismissButton = {
            if (!consoleState.isRunning) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
