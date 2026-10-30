package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

data class ImportedAssetsSummary(
    val workspaceDirPath: String,
    val entryHtmlFile: String,
    val fileCount: Int,
    val totalBytes: Long,
    val fileNames: List<String>
)

class StudioRepository(
    private val context: Context,
    private val dao: StudioDao
) {
    val allProjects: Flow<List<WebAppProjectEntity>> = dao.observeProjects()
    val allBuilds: Flow<List<BuildRecordEntity>> = dao.observeBuildRecords()

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        if (dao.getAllProjectsOnce().isNotEmpty()) return@withContext

        // Seed 1: Local HTML5 + CSS3 + JS Interactive Calculator & Hardware Bridge App
        val sampleDir = File(context.filesDir, "projects/sample_html_suite/assets").apply { mkdirs() }
        val indexFile = File(sampleDir, "index.html")
        val styleFile = File(sampleDir, "styles.css")
        val scriptFile = File(sampleDir, "app.js")

        styleFile.writeText(
            """
            * { box-sizing: border-box; margin: 0; padding: 0; font-family: system-ui, -apple-system, sans-serif; }
            body { background: #0B0F17; color: #F8FAFC; padding: 20px; }
            .card { background: #131B2E; border: 1px solid #283548; border-radius: 16px; padding: 18px; margin-bottom: 16px; }
            .badge { display: inline-block; background: #064E3B; color: #34D399; padding: 4px 10px; border-radius: 999px; font-size: 12px; font-weight: 700; margin-bottom: 10px; }
            h1 { font-size: 22px; margin-bottom: 6px; }
            p { color: #94A3B8; font-size: 14px; line-height: 1.5; margin-bottom: 12px; }
            .btn-row { display: flex; gap: 10px; flex-wrap: wrap; margin-top: 10px; }
            button { background: #3B82F6; color: white; border: none; padding: 10px 16px; border-radius: 10px; font-weight: 600; cursor: pointer; }
            button.secondary { background: #1E293B; color: #E2E8F0; border: 1px solid #334155; }
            #counter-val { font-size: 32px; font-weight: 800; color: #60A5FA; margin: 8px 0; }
            """.trimIndent()
        )

        scriptFile.writeText(
            """
            let count = Number(localStorage.getItem('studio_counter') || 0);
            const counterEl = document.getElementById('counter-val');
            function render() { counterEl.textContent = count; }
            function updateCount(delta) {
                count += delta;
                localStorage.setItem('studio_counter', count);
                render();
                if (window.AndroidAPI && window.AndroidAPI.vibrate) {
                    window.AndroidAPI.vibrate(30);
                }
            }
            function triggerNativeToast() {
                if (window.AndroidAPI && window.AndroidAPI.showToast) {
                    window.AndroidAPI.showToast('Native AndroidAPI Bridge Active! Zero Watermarks.');
                } else {
                    alert('Native AndroidAPI Bridge Active!');
                }
            }
            render();
            """.trimIndent()
        )

        indexFile.writeText(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Offline WebApp Suite</title>
                <link rel="stylesheet" href="styles.css" />
            </head>
            <body>
                <div class="card">
                    <span class="badge">HTML5 + CSS3 + JS BUNDLE</span>
                    <h1>Pulse Counter &amp; Native Bridge</h1>
                    <p>Bundled locally with DOM Storage persistence and native AndroidAPI hardware vibration &amp; toast hooks.</p>
                    <div id="counter-val">0</div>
                    <div class="btn-row">
                        <button onclick="updateCount(1)">+ Increment</button>
                        <button class="secondary" onclick="updateCount(-1)">- Decrement</button>
                        <button class="secondary" onclick="triggerNativeToast()">Test AndroidAPI</button>
                    </div>
                </div>
            </body>
            </html>
            """.trimIndent()
        )

        val totalSize = indexFile.length() + styleFile.length() + scriptFile.length()

        dao.insertProject(
            WebAppProjectEntity(
                appName = "Pulse Offline Suite",
                packageName = "com.studio.pulseoffline",
                versionName = "1.2.0",
                versionCode = 3,
                autoIncrementVersion = true,
                minSdk = 24,
                sourceType = SourceType.LOCAL_FILES.name,
                websiteUrl = "",
                workspaceDirPath = sampleDir.absolutePath,
                entryHtmlFile = "index.html",
                assetFileCount = 3,
                assetTotalBytes = totalSize,
                assetSummaryList = "index.html, styles.css, app.js",
                iconMonogram = "PS",
                iconBgHex = "#2563EB",
                iconTextHex = "#FFFFFF",
                splashEnabled = true,
                splashBgHex = "#0B0F17",
                splashTagline = "100% Offline HTML5 Native Package",
                splashDurationMs = 1200,
                pullToRefresh = true,
                enableJavaScript = true,
                enableDomStorage = true,
                enableNativeJsBridge = true
            )
        )

        // Seed 2: Live Website URL to APK Project
        dao.insertProject(
            WebAppProjectEntity(
                appName = "MDN Web Docs Portal",
                packageName = "com.web2apk.mdndocs",
                versionName = "2.0.0",
                versionCode = 1,
                autoIncrementVersion = true,
                minSdk = 24,
                sourceType = SourceType.WEBSITE_URL.name,
                websiteUrl = "https://developer.mozilla.org",
                workspaceDirPath = "",
                entryHtmlFile = "",
                assetFileCount = 0,
                assetTotalBytes = 0L,
                assetSummaryList = "Live URL: https://developer.mozilla.org",
                iconMonogram = "MD",
                iconBgHex = "#059669",
                iconTextHex = "#FFFFFF",
                splashEnabled = true,
                splashBgHex = "#064E3B",
                splashTagline = "Fast Documentation Wrapper",
                splashDurationMs = 1200,
                pullToRefresh = true,
                backButtonHistory = true,
                externalLinkRule = ExternalLinkRule.OPEN_IN_BROWSER.name
            )
        )
    }

    suspend fun saveProject(project: WebAppProjectEntity): Long = withContext(Dispatchers.IO) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        if (updated.id == 0L) {
            dao.insertProject(updated)
        } else {
            dao.updateProject(updated)
            updated.id
        }
    }

    suspend fun deleteProject(project: WebAppProjectEntity) = withContext(Dispatchers.IO) {
        if (project.workspaceDirPath.isNotBlank()) {
            File(project.workspaceDirPath).parentFile?.deleteRecursively()
        }
        dao.deleteProjectById(project.id)
    }

    suspend fun insertBuildRecord(record: BuildRecordEntity): Long = withContext(Dispatchers.IO) {
        dao.insertBuildRecord(record)
    }

    suspend fun deleteBuildRecord(record: BuildRecordEntity) = withContext(Dispatchers.IO) {
        File(record.filePath).delete()
        dao.deleteBuildRecordById(record.id)
    }

    suspend fun importWebAssets(
        uris: List<Uri>,
        existingWorkspacePath: String
    ): ImportedAssetsSummary = withContext(Dispatchers.IO) {
        val folderId = if (existingWorkspacePath.isNotBlank()) {
            File(existingWorkspacePath)
        } else {
            File(context.filesDir, "projects/proj_${System.currentTimeMillis()}/assets")
        }
        if (folderId.exists()) {
            folderId.deleteRecursively()
        }
        folderId.mkdirs()

        for (uri in uris) {
            val fileName = resolveDisplayName(uri) ?: "file_${System.currentTimeMillis()}.html"
            if (fileName.lowercase().endsWith(".zip")) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val cleanName = entry.name.removePrefix("/").replace("..", "")
                            if (cleanName.isNotBlank() && !cleanName.startsWith("__MACOSX")) {
                                val outFile = File(folderId, cleanName)
                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } else {
                val outFile = File(folderId, fileName)
                outFile.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { fos ->
                        input.copyTo(fos)
                    }
                }
            }
        }

        // Flatten single nested root folder if ZIP extracted into a single sub-directory
        val topChildren = folderId.listFiles()?.toList().orEmpty()
        if (topChildren.size == 1 && topChildren.first().isDirectory) {
            val subDir = topChildren.first()
            subDir.listFiles()?.forEach { child ->
                child.renameTo(File(folderId, child.name))
            }
            subDir.delete()
        }

        val allFiles = folderId.walkTopDown().filter { it.isFile }.toList()
        val htmlFiles = allFiles.filter {
            it.extension.equals("html", ignoreCase = true) ||
                it.extension.equals("htm", ignoreCase = true)
        }

        // If user uploaded only CSS/JS or single HTML not named index.html, determine entry file
        val entryHtml = when {
            htmlFiles.any { it.name.equals("index.html", ignoreCase = true) } ->
                htmlFiles.first { it.name.equals("index.html", ignoreCase = true) }
                    .relativeTo(folderId).path
            htmlFiles.isNotEmpty() ->
                htmlFiles.first().relativeTo(folderId).path
            else -> {
                // Auto-create an index.html wrapper linking uploaded CSS and JS files
                val cssLinks = allFiles.filter { it.extension.equals("css", ignoreCase = true) }
                    .joinToString("\n") { "<link rel=\"stylesheet\" href=\"${it.relativeTo(folderId).path}\" />" }
                val jsScripts = allFiles.filter { it.extension.equals("js", ignoreCase = true) }
                    .joinToString("\n") { "<script src=\"${it.relativeTo(folderId).path}\"></script>" }
                val autoIndex = File(folderId, "index.html")
                autoIndex.writeText(
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta charset="UTF-8" />
                        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                        <title>WebApp</title>
                        $cssLinks
                    </head>
                    <body>
                        <div id="app"></div>
                        $jsScripts
                    </body>
                    </html>
                    """.trimIndent()
                )
                "index.html"
            }
        }

        val finalFiles = folderId.walkTopDown().filter { it.isFile }.toList()
        val totalSize = finalFiles.sumOf { it.length() }
        val names = finalFiles.map { it.relativeTo(folderId).path }

        ImportedAssetsSummary(
            workspaceDirPath = folderId.absolutePath,
            entryHtmlFile = entryHtml,
            fileCount = finalFiles.size,
            totalBytes = totalSize,
            fileNames = names
        )
    }

    suspend fun importCustomIcon(uri: Uri): String = withContext(Dispatchers.IO) {
        val iconDir = File(context.filesDir, "projects/icons").apply { mkdirs() }
        val iconFile = File(iconDir, "icon_${System.currentTimeMillis()}.png")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(iconFile).use { fos ->
                input.copyTo(fos)
            }
        }
        iconFile.absolutePath
    }

    private fun resolveDisplayName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(index)
            }
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
