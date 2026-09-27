package com.example

import com.example.util.FileOpener
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testPdfAndImageFormatIdentification() {
    val pdfFile = File("sample_document.pdf")
    val pdfInfo = FileOpener.identifyFormat(pdfFile)
    assertTrue(pdfInfo.isPdf)
    assertEquals("application/pdf", pdfInfo.mimeType)

    val imageFile = File("picture.png")
    val imageInfo = FileOpener.identifyFormat(imageFile)
    assertEquals("image/png", imageInfo.mimeType)

    val apkFile = File("app-release.apk")
    val apkInfo = FileOpener.identifyFormat(apkFile)
    assertTrue(apkInfo.isApk)
    assertEquals("application/vnd.android.package-archive", apkInfo.mimeType)
  }

  @Test
  fun testZoomScaleMath() {
    val minScale = 1.0f
    val maxScale = 6.0f
    val zoomFactor = 1.5f
    val currentScale = 2.0f
    val newScale = (currentScale * zoomFactor).coerceIn(minScale, maxScale)
    assertEquals(3.0f, newScale, 0.001f)

    // Test max scale bound
    val overZoom = (currentScale * 5.0f).coerceIn(minScale, maxScale)
    assertEquals(maxScale, overZoom, 0.001f)
  }

  @Test
  fun testFileOperationProgressCalculation() {
    val progress = com.example.data.model.FileOperationProgress(
      operationId = "op_123",
      title = "Copying files",
      type = com.example.data.model.FileOperationType.COPY,
      totalFiles = 20,
      processedFiles = 10,
      totalBytes = 2000L,
      processedBytes = 1000L,
      speedBytesPerSec = 500L,
      estimatedRemainingSeconds = 2L
    )

    assertEquals(0.5f, progress.progressFraction, 0.001f)
    assertEquals("2s remaining", progress.formattedRemainingTime)
    assertTrue(progress.formattedSpeed.contains("/s"))
  }

  @Test
  fun testDuplicateGroupWasteCalculation() {
    val files = listOf(File("file1.png"), File("file2.png"), File("file3.png"))
    val group = com.example.data.model.DuplicateGroup(
      fileSize = 1024L,
      hash = "dummyhash123",
      files = files
    )
    assertEquals(3, group.count)
    assertEquals(2048L, group.wasteSize) // 1024 * (3 - 1)
  }
}


