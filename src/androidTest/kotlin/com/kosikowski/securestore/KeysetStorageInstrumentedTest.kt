package com.kosikowski.securestore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    private val prefsKeysetFile = "secure_storage_prefs_key_$namespace"
    private val prefsKeyset = "secure_storage_prefs_keyset_pref_$namespace"
    private val keysetFiles = listOf("secure_storage_tink_key_$namespace", prefsKeysetFile)

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
    fun keysetsInTheLegacyCredentialEncryptedLocation_areCopiedAndDataStaysReadable() =
        runBlocking {
            simulateLegacyDeviceProtectedStore()

            val upgraded = SecureStorageImpl(context, config(StorageMode.DEVICE_PROTECTED))

            assertEquals(VALUE, upgraded.getString(KEY))
            assertArrayEquals(BLOB_CONTENT, upgraded.readBlob(BLOB))
            keysetFiles.forEach {
                assertTrue("$it copied to device-protected storage", deviceProtectedContext.sharedPreferencesFile(it).exists())
            }
        }

    @Test
    fun sameNamespaceInBothModes_upgradeAndResetKeepTheCredentialProtectedStoreReadable() =
        runBlocking {
            SecureStorageImpl(context, config(StorageMode.CREDENTIAL_PROTECTED)).putString(KEY, CREDENTIAL_PROTECTED_VALUE)
            // Up to 1.0.0 a device-protected store with the same namespace encrypted its data with these keysets.
            keysetFiles.forEach { context.sharedPreferencesFile(it).copyTo(deviceProtectedContext.sharedPreferencesFile(it)) }
            SecureStorageImpl(context, config(StorageMode.DEVICE_PROTECTED)).putString(KEY, VALUE)
            keysetFiles.forEach { assertTrue(deviceProtectedContext.deleteSharedPreferences(it)) }

            val upgraded = SecureStorageImpl(context, config(StorageMode.DEVICE_PROTECTED))
            assertEquals(VALUE, upgraded.getString(KEY))
            assertEquals(CREDENTIAL_PROTECTED_VALUE, SecureStorageImpl(context, config(StorageMode.CREDENTIAL_PROTECTED)).getString(KEY))

            upgraded.reset()
            assertEquals(CREDENTIAL_PROTECTED_VALUE, SecureStorageImpl(context, config(StorageMode.CREDENTIAL_PROTECTED)).getString(KEY))
        }

    @Test
    fun resetAfterUpgrade_startsWithNewKeysetsInsteadOfCopyingTheLegacyOnesAgain() =
        runBlocking {
            simulateLegacyDeviceProtectedStore()
            val upgraded = SecureStorageImpl(context, config(StorageMode.DEVICE_PROTECTED))
            assertEquals(VALUE, upgraded.getString(KEY))

            upgraded.reset()
            upgraded.putString(KEY, VALUE)

            assertNotEquals(
                context.keyset(prefsKeysetFile, prefsKeyset),
                deviceProtectedContext.keyset(prefsKeysetFile, prefsKeyset),
            )
        }

    private suspend fun simulateLegacyDeviceProtectedStore() {
        SecureStorageImpl(context, config(StorageMode.DEVICE_PROTECTED)).apply {
            putString(KEY, VALUE)
            saveBlob(BLOB, BLOB_CONTENT)
        }
        keysetFiles.forEach {
            assertTrue(context.moveSharedPreferencesFrom(deviceProtectedContext, it))
            assertFalse(deviceProtectedContext.sharedPreferencesFile(it).exists())
        }
    }

    private fun Context.keyset(file: String, name: String) = getSharedPreferences(file, Context.MODE_PRIVATE).getString(name, null)

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
        const val CREDENTIAL_PROTECTED_VALUE = "credential-protected-value"
        const val BLOB = "certificate.bin"
        val BLOB_CONTENT = byteArrayOf(1, 2, 3, 4, 5)
    }
}
