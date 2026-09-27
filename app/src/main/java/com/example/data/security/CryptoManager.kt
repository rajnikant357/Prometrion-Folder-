package com.example.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class EncryptionResult(
    val encryptedFile: File,
    val saltHex: String,
    val ivHex: String,
    val originalSize: Long
)

class CryptoManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vault_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BIT = 128
        private const val IV_LENGTH_BYTE = 12
        private const val SALT_LENGTH_BYTE = 16
        private const val ITERATION_COUNT = 65536
        private const val KEY_LENGTH_BIT = 256

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_VAULT_INITIALIZED = "vault_initialized"
    }

    fun isVaultInitialized(): Boolean {
        return prefs.getBoolean(KEY_VAULT_INITIALIZED, false)
    }

    fun setupPin(pin: String): Boolean {
        if (pin.length < 4) return false
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTE)
        random.nextBytes(salt)

        val hash = hashPin(pin, salt)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putBoolean(KEY_VAULT_INITIALIZED, true)
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val saltStr = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expectedHashStr = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val actualHash = hashPin(pin, salt)
        val actualHashStr = Base64.encodeToString(actualHash, Base64.NO_WRAP)
        return expectedHashStr == actualHashStr
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        return setupPin(newPin)
    }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BIT)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }

    fun getVaultDir(): File {
        val dir = File(context.filesDir, "encrypted_vault")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun encryptFile(sourceFile: File, pin: String): EncryptionResult? {
        return try {
            if (!sourceFile.exists() || !sourceFile.isFile) return null

            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTE)
            random.nextBytes(salt)

            val iv = ByteArray(IV_LENGTH_BYTE)
            random.nextBytes(iv)

            val secretKey = deriveKey(pin, salt)
            val cipher = Cipher.getInstance(ALGORITHM)
            val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

            val vaultDir = getVaultDir()
            val encryptedFileName = "enc_" + System.currentTimeMillis() + "_" + random.nextInt(10000) + ".dat"
            val targetFile = File(vaultDir, encryptedFileName)

            FileInputStream(sourceFile).use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val encryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (encryptedBytes != null) {
                            output.write(encryptedBytes)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        output.write(finalBytes)
                    }
                }
            }

            EncryptionResult(
                encryptedFile = targetFile,
                saltHex = bytesToHex(salt),
                ivHex = bytesToHex(iv),
                originalSize = sourceFile.length()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decryptToBytes(encryptedFile: File, pin: String, saltHex: String, ivHex: String): ByteArray? {
        return try {
            if (!encryptedFile.exists()) return null
            val salt = hexToBytes(saltHex)
            val iv = hexToBytes(ivHex)

            val secretKey = deriveKey(pin, salt)
            val cipher = Cipher.getInstance(ALGORITHM)
            val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

            val encryptedBytes = encryptedFile.readBytes()
            cipher.doFinal(encryptedBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decryptToFile(encryptedFile: File, destinationFile: File, pin: String, saltHex: String, ivHex: String): Boolean {
        return try {
            if (!encryptedFile.exists()) return false
            val decryptedBytes = decryptToBytes(encryptedFile, pin, saltHex, ivHex) ?: return false

            destinationFile.parentFile?.mkdirs()
            FileOutputStream(destinationFile).use { output ->
                output.write(decryptedBytes)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun deleteEncryptedFile(encryptedFileName: String): Boolean {
        val file = File(getVaultDir(), encryptedFileName)
        return if (file.exists()) file.delete() else true
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt()
            result.append(hexChars[(i shr 4) and 0x0F])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
