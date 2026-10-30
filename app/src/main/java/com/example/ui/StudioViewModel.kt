package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.builder.ApkPackagingEngine
import com.example.data.BuildOutputFormat
import com.example.data.BuildRecordEntity
import com.example.data.SourceType
import com.example.data.StudioRepository
import com.example.data.WebAppProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class StudioTab {
    DASHBOARD,
    CONFIGURATOR,
    BUILDS
}

data class BuildConsoleState(
    val isVisible: Boolean = false,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val stepTitle: String = "",
    val logs: List<String> = emptyList(),
    val completedRecord: BuildRecordEntity? = null,
    val errorMessage: String? = null
)

data class StudioUiState(
    val activeTab: StudioTab = StudioTab.DASHBOARD,
    val activeStep: Int = 0,
    val draftProject: WebAppProjectEntity = WebAppProjectEntity(),
    val projects: List<WebAppProjectEntity> = emptyList(),
    val builds: List<BuildRecordEntity> = emptyList(),
    val buildConsole: BuildConsoleState = BuildConsoleState(),
    val sandboxProject: WebAppProjectEntity? = null,
    val showJsBridgeDialog: Boolean = false,
    val isDarkTheme: Boolean = true,
    val statusBanner: String? = null
) {
    val isPackageNameValid: Boolean
        get() = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
            .matches(draftProject.packageName.trim())

    val isUrlValid: Boolean
        get() = draftProject.websiteUrl.trim().startsWith("http://") ||
            draftProject.websiteUrl.trim().startsWith("https://")
}

class StudioViewModel(
    private val appContext: Context,
    private val repository: StudioRepository,
    private val packagingEngine: ApkPackagingEngine
) : ViewModel() {

    private val internalState = MutableStateFlow(StudioUiState())

    val uiState: StateFlow<StudioUiState> = combine(
        internalState,
        repository.allProjects,
        repository.allBuilds
    ) { state, projects, builds ->
        state.copy(
            projects = projects,
            builds = builds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
            val existing = repository.allProjects
        }
    }

    fun selectTab(tab: StudioTab) {
        internalState.update { it.copy(activeTab = tab, statusBanner = null) }
    }

    fun selectStep(step: Int) {
        internalState.update { it.copy(activeStep = step.coerceIn(0, 3)) }
    }

    fun toggleTheme() {
        internalState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun setShowJsBridgeDialog(show: Boolean) {
        internalState.update { it.copy(showJsBridgeDialog = show) }
    }

    fun clearStatusBanner() {
        internalState.update { it.copy(statusBanner = null) }
    }

    fun startNewProject(sourceType: SourceType) {
        val suffix = (100..999).random()
        val defaultName = if (sourceType == SourceType.LOCAL_FILES) "My HTML App" else "My Web App"
        val defaultPkg = if (sourceType == SourceType.LOCAL_FILES) {
            "com.web2apk.htmlapp$suffix"
        } else {
            "com.web2apk.webapp$suffix"
        }
        val newDraft = WebAppProjectEntity(
            appName = defaultName,
            packageName = defaultPkg,
            sourceType = sourceType.name,
            websiteUrl = if (sourceType == SourceType.WEBSITE_URL) "https://example.com" else "",
            iconMonogram = if (sourceType == SourceType.LOCAL_FILES) "HT" else "WB",
            iconBgHex = if (sourceType == SourceType.LOCAL_FILES) "#2563EB" else "#059669"
        )
        internalState.update {
            it.copy(
                draftProject = newDraft,
                activeTab = StudioTab.CONFIGURATOR,
                activeStep = 0,
                statusBanner = null
            )
        }
    }

    fun openProjectForEditing(project: WebAppProjectEntity) {
        internalState.update {
            it.copy(
                draftProject = project,
                activeTab = StudioTab.CONFIGURATOR,
                activeStep = 0,
                statusBanner = null
            )
        }
    }

    fun updateDraft(transform: (WebAppProjectEntity) -> WebAppProjectEntity) {
        internalState.update { state ->
            val updated = transform(state.draftProject)
            state.copy(draftProject = updated)
        }
    }

    fun saveCurrentDraft(showFeedback: Boolean = true) {
        viewModelScope.launch {
            val current = internalState.value.draftProject
            val savedId = repository.saveProject(current)
            internalState.update {
                it.copy(
                    draftProject = current.copy(id = savedId),
                    statusBanner = if (showFeedback) "Project '${current.appName}' saved (0 watermarks)." else it.statusBanner
                )
            }
        }
    }

    fun deleteProject(project: WebAppProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project)
            internalState.update {
                it.copy(statusBanner = "Deleted project '${project.appName}'.")
            }
        }
    }

    fun importWebFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            try {
                val current = internalState.value.draftProject
                val summary = repository.importWebAssets(uris, current.workspaceDirPath)
                val updated = current.copy(
                    sourceType = SourceType.LOCAL_FILES.name,
                    workspaceDirPath = summary.workspaceDirPath,
                    entryHtmlFile = summary.entryHtmlFile,
                    assetFileCount = summary.fileCount,
                    assetTotalBytes = summary.totalBytes,
                    assetSummaryList = summary.fileNames.take(8).joinToString(", ")
                )
                val savedId = repository.saveProject(updated)
                internalState.update {
                    it.copy(
                        draftProject = updated.copy(id = savedId),
                        statusBanner = "Imported ${summary.fileCount} file(s) • Entry: ${summary.entryHtmlFile}"
                    )
                }
            } catch (e: Exception) {
                internalState.update {
                    it.copy(statusBanner = "Failed to import files: ${e.localizedMessage}")
                }
            }
        }
    }

    fun importCustomIcon(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            try {
                val iconPath = repository.importCustomIcon(uri)
                internalState.update { state ->
                    state.copy(
                        draftProject = state.draftProject.copy(customIconPath = iconPath),
                        statusBanner = "Custom launcher icon imported."
                    )
                }
            } catch (e: Exception) {
                internalState.update {
                    it.copy(statusBanner = "Could not load icon: ${e.localizedMessage}")
                }
            }
        }
    }

    fun launchSandbox(project: WebAppProjectEntity = internalState.value.draftProject) {
        viewModelScope.launch {
            val savedId = repository.saveProject(project)
            val savedProject = project.copy(id = savedId)
            internalState.update {
                it.copy(
                    draftProject = savedProject,
                    sandboxProject = savedProject
                )
            }
        }
    }

    fun closeSandbox() {
        internalState.update { it.copy(sandboxProject = null) }
    }

    fun buildProject(
        project: WebAppProjectEntity = internalState.value.draftProject,
        format: BuildOutputFormat
    ) {
        viewModelScope.launch {
            // Prepare version auto-increment if enabled and project was previously built
            val nextVersionCode = if (project.autoIncrementVersion && project.buildCount > 0) {
                project.versionCode + 1
            } else {
                project.versionCode
            }
            val projectToBuild = project.copy(
                versionCode = nextVersionCode,
                buildCount = project.buildCount + 1
            )
            val savedId = repository.saveProject(projectToBuild)
            val persistedProject = projectToBuild.copy(id = savedId)

            internalState.update {
                it.copy(
                    draftProject = persistedProject,
                    buildConsole = BuildConsoleState(
                        isVisible = true,
                        isRunning = true,
                        progress = 0.05f,
                        stepTitle = "Initializing Clean Build Pipeline...",
                        logs = listOf(
                            "[Studio] Starting watermark-free build for '${persistedProject.appName}'",
                            "[Target] Format=${format.label}"
                        )
                    )
                )
            }

            try {
                val record = packagingEngine.buildArtifact(persistedProject, format) { stepUpdate ->
                    internalState.update { state ->
                        state.copy(
                            buildConsole = state.buildConsole.copy(
                                progress = stepUpdate.progress,
                                stepTitle = stepUpdate.stepTitle,
                                logs = state.buildConsole.logs + stepUpdate.logLine
                            )
                        )
                    }
                }
                val recordId = repository.insertBuildRecord(record)
                val finalRecord = record.copy(id = recordId)

                internalState.update { state ->
                    state.copy(
                        buildConsole = state.buildConsole.copy(
                            isRunning = false,
                            progress = 1f,
                            stepTitle = "Build Ready • 0 Watermarks",
                            completedRecord = finalRecord
                        )
                    )
                }
            } catch (e: Exception) {
                internalState.update { state ->
                    state.copy(
                        buildConsole = state.buildConsole.copy(
                            isRunning = false,
                            errorMessage = e.localizedMessage ?: "Build failed"
                        )
                    )
                }
            }
        }
    }

    fun dismissBuildConsole() {
        internalState.update {
            it.copy(buildConsole = BuildConsoleState(isVisible = false))
        }
    }

    fun exportBuildToUri(record: BuildRecordEntity, destinationUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val srcFile = File(record.filePath)
                if (!srcFile.exists()) {
                    internalState.update { it.copy(statusBanner = "Build file not found on disk.") }
                    return@launch
                }
                appContext.contentResolver.openOutputStream(destinationUri)?.use { out ->
                    srcFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                internalState.update {
                    it.copy(statusBanner = "Saved '${record.fileName}' to device storage.")
                }
            } catch (e: Exception) {
                internalState.update {
                    it.copy(statusBanner = "Export failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun shareBuildArtifact(context: Context, record: BuildRecordEntity) {
        try {
            val file = File(record.filePath)
            if (!file.exists()) {
                internalState.update { it.copy(statusBanner = "Artifact file missing; please rebuild.") }
                return
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = if (record.fileName.endsWith(".apk")) {
                "application/vnd.android.package-archive"
            } else {
                "application/zip"
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, record.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Export ${record.fileName}"))
        } catch (e: Exception) {
            internalState.update {
                it.copy(statusBanner = "Unable to open share sheet: ${e.localizedMessage}")
            }
        }
    }

    fun deleteBuildRecord(record: BuildRecordEntity) {
        viewModelScope.launch {
            repository.deleteBuildRecord(record)
            internalState.update {
                it.copy(statusBanner = "Deleted build '${record.fileName}'.")
            }
        }
    }

    class Factory(
        private val appContext: Context,
        private val repository: StudioRepository,
        private val packagingEngine: ApkPackagingEngine
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StudioViewModel(appContext, repository, packagingEngine) as T
        }
    }
}
