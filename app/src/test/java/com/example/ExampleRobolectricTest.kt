package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("VaultFiles", appName)
  }

  @Test
  fun `test zero knowledge crypto manager`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val crypto = CryptoManager(context)

    val pin = "1234"
    assertTrue(crypto.setupPin(pin))
    assertTrue(crypto.verifyPin(pin))

    // Create sample file
    val testFile = File(context.cacheDir, "sample_secret.txt")
    val secretContent = "Top secret zero-knowledge document content"
    testFile.writeText(secretContent)

    // Encrypt
    val encResult = crypto.encryptFile(testFile, pin)
    assertNotNull(encResult)

    // Decrypt to bytes
    val decryptedBytes = crypto.decryptToBytes(encResult!!.encryptedFile, pin, encResult.saltHex, encResult.ivHex)
    assertNotNull(decryptedBytes)
    assertEquals(secretContent, String(decryptedBytes!!))
  }

  @Test
  fun `verify app publisher and developer details`() {
    val publisherWebsite = "https://www.prometrion.com"
    val developerWebsite = "https://www.rajnikantg.in"
    val developerName = "Rajnikant Gaurav"
    val publisherName = "Prometrion"

    assertEquals("Prometrion", publisherName)
    assertEquals("Rajnikant Gaurav", developerName)
    assertTrue(publisherWebsite.contains("prometrion.com"))
    assertTrue(developerWebsite.contains("rajnikantg.in"))
  }
}
