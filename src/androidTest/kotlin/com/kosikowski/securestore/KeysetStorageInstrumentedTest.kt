package com.kosikowski.securestore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class KeysetStorageInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>().applicationContext
    private val deviceProtectedContext: Context = context.createDeviceProtectedStorageContext()
    private val namespace = "keyset_storage_${System.nanoTime()}"

    private val keysetFiles = listOf("secure_storage_tink_key_$namespace", "secure_storage_prefs_key_$namespace")

    @After
    fun tearDown() {
        for (storage in listOf(context, deviceProtectedContext)) {
            (keysetFiles + "secure_storage_prefs_$namespace").forEach { storage.deleteSharedPreferences(it) }
            File(storage.filesDir, "secure_blobs_$namespace").deleteRecursively()
        }
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry("secure_store_master_key_$namespace")
    }

    @Test
    fun deviceProtectedMode_keepsKeysetsInDeviceProtectedStorage() =
        runBlocking {
            val storage = SecureStorageImpl(context, config(StorageMode.DEVICE_PROTECTED))

            storage.putString(KEY, VALUE)
            storage.saveBlob(BLOB, BLOB_CONTENT)

            keysetFiles.forEach {
                assertTrue("$it in device-protected storage", deviceProtectedContext.sharedPreferencesFile(it).exists())
                assertFalse("$it in credential-encrypted storage", context.sharedPreferencesFile(it).exists())
            }
        }

    @Test
    fun credentialProtectedMode_keepsKeysetsInCredentialEncryptedStorage() =
        runBlocking {
            val storage = SecureStorageImpl(context, config(StorageMode.CREDENTIAL_PROTECTED))

            storage.putString(KEY, VALUE)
            storage.saveBlob(BLOB, BLOB_CONTENT)

            keysetFiles.forEach {
                assertTrue("$it in credential-encrypted storage", context.sharedPreferencesFile(it).exists())
                assertFalse("$it in device-protected storage", deviceProtectedContext.sharedPreferencesFile(it).exists())
            }
        }

    @Test
    fun keysetsInTheLegacyCredentialEncryptedLocation_areMovedAndDataStaysReadable() =
        runBlocking {
            SecureStorageImpl(context, config(StorageMode.DEVICE_PROTECTED)).apply {
                putString(KEY, VALUE)
                saveBlob(BLOB, BLOB_CONTENT)
            }
            keysetFiles.forEach {
                assertTrue(context.moveSharedPreferencesFrom(deviceProtectedContext, it))
                assertFalse(deviceProtectedContext.sharedPreferencesFile(it).exists())
            }

            val upgraded = SecureStorageImpl(context, config(StorageMode.DEVICE_PROTECTED))

            assertEquals(VALUE, upgraded.getString(KEY))
            assertArrayEquals(BLOB_CONTENT, upgraded.readBlob(BLOB))
            keysetFiles.forEach {
                assertTrue("$it moved to device-protected storage", deviceProtectedContext.sharedPreferencesFile(it).exists())
                assertFalse("$it left in credential-encrypted storage", context.sharedPreferencesFile(it).exists())
            }
        }

    private fun Context.sharedPreferencesFile(name: String) = File(dataDir, "shared_prefs/$name.xml")

    private fun config(mode: StorageMode) =
        SecureStoreConfig.Builder()
            .storageMode(mode)
            .namespace(namespace)
            .decryptionFailurePolicy(DecryptionFailurePolicy.THROW_EXCEPTION)
            .build()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY = "token"
        const val VALUE = "secret-value"
        const val BLOB = "certificate.bin"
        val BLOB_CONTENT = byteArrayOf(1, 2, 3, 4, 5)
    }
}
