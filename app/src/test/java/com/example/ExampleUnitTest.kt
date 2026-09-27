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
}

