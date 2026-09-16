package com.kosikowski.securestore

import android.util.Base64
import com.google.crypto.tink.DeterministicAead

/**
 * Encrypts key and file names deterministically, so a name maps to the same stored name every time and
 * can be found again. Keys and file names use different associated data, so a key and a file with the
 * same name are not stored under the same name.
 */
internal class NameCipher(
    private val daead: DeterministicAead,
) {
    fun encryptKey(key: String): String = encrypt(key, KEY_NAMES)

    fun decryptKey(storedKey: String): String = decrypt(storedKey, KEY_NAMES)

    fun encryptFileName(fileName: String): String = encrypt(fileName, FILE_NAMES)

    fun decryptFileName(storedFileName: String): String = decrypt(storedFileName, FILE_NAMES)

    private fun encrypt(name: String, names: ByteArray): String =
        Base64.encodeToString(daead.encryptDeterministically(name.toByteArray(Charsets.UTF_8), names), BASE64_FLAGS)

    private fun decrypt(storedName: String, names: ByteArray): String =
        String(daead.decryptDeterministically(Base64.decode(storedName, BASE64_FLAGS), names), Charsets.UTF_8)

    private companion object {
        const val BASE64_FLAGS = Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        val KEY_NAMES = "key".toByteArray(Charsets.UTF_8)
        val FILE_NAMES = "file".toByteArray(Charsets.UTF_8)
    }
}
