package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.builder.ApkPackagingEngine
import com.example.data.BuildOutputFormat
import com.example.data.SourceType
import com.example.data.WebAppProjectEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and verify APK and Studio ZIP builder`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Web2APK Studio", appName)

        val engine = ApkPackagingEngine(context)
        val project = WebAppProjectEntity(
            id = 1L,
            appName = "Test WebApp",
            packageName = "com.test.webapp",
            versionName = "1.0.0",
            versionCode = 1,
            sourceType = SourceType.WEBSITE_URL.name,
            websiteUrl = "https://example.com"
        )

        val signedRecord = engine.buildArtifact(project, BuildOutputFormat.SIGNED_APK) {}
        assertTrue(File(signedRecord.filePath).exists())
        assertTrue(signedRecord.fileSizeBytes > 0)
        assertTrue(signedRecord.sha256Signature.isNotBlank())

        val zipRecord = engine.buildArtifact(project, BuildOutputFormat.ANDROID_STUDIO_ZIP) {}
        assertTrue(File(zipRecord.filePath).exists())
        assertTrue(zipRecord.fileSizeBytes > 0)
    }
}
