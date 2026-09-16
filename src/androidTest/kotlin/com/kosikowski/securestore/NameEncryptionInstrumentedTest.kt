package com.kosikowski.securestore

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class NameEncryptionInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>().applicationContext
    private val deviceProtectedContext: Context = context.createDeviceProtectedStorageContext()
    private val namespace = "name_encryption_${System.nanoTime()}"
    private val config =
        SecureStoreConfig.Builder()
            .namespace(namespace)
            .encryptKeys(true)
            .encryptFileNames(true)
            .decryptionFailurePolicy(DecryptionFailurePolicy.THROW_EXCEPTION)
            .build()

    private val values get() = deviceProtectedContext.getSharedPreferences("secure_storage_prefs_$namespace", Context.MODE_PRIVATE)
    private val legacyKeyset = "secure_storage_metadata_key_$namespace"
    private val blobDirectory get() = File(deviceProtectedContext.filesDir, "secure_blobs_$namespace")

    @After
    fun tearDown() =
        runBlocking {
            SecureStorageImpl(context, config).reset()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("secure_store_master_key_$namespace")
        }

    @Test
    fun encryptedKeys_valuesCanBeReadUpdatedAndRemoved() =
        runBlocking {
            val storage = SecureStorageImpl(context, config)

            storage.putString(KEY, VALUE)
            assertEquals(VALUE, storage.getString(KEY))
            assertTrue(storage.contains(KEY))

            storage.putString(KEY, OTHER_VALUE)
            assertEquals(OTHER_VALUE, SecureStorageImpl(context, config).getString(KEY))
            assertEquals(setOf(KEY), storage.getAllKeys())

            storage.putObject(OBJECT_KEY, Credentials("user", 42), Credentials.serializer())
            assertEquals(Credentials("user", 42), storage.getObject(OBJECT_KEY, Credentials.serializer()))

            storage.removeString(KEY)
            assertNull(storage.getString(KEY))
            assertFalse(storage.contains(KEY))
            assertEquals(setOf(OBJECT_KEY), storage.getAllKeys())
        }

    @Test
    fun encryptedFileNames_blobsCanBeReadReplacedAndDeleted() =
        runBlocking {
            val storage = SecureStorageImpl(context, config)

            storage.saveBlob(BLOB, BLOB_CONTENT)
            assertArrayEquals(BLOB_CONTENT, storage.readBlob(BLOB))
            assertTrue(storage.blobExists(BLOB))

            storage.saveBlob(BLOB, OTHER_BLOB_CONTENT)
            assertArrayEquals(OTHER_BLOB_CONTENT, SecureStorageImpl(context, config).readBlob(BLOB))
            assertEquals(setOf(BLOB), storage.getAllBlobNames())
            assertEquals(1, blobDirectory.list()?.size)

            assertTrue(storage.deleteBlob(BLOB))
            assertFalse(storage.blobExists(BLOB))
            assertNull(storage.readBlob(BLOB))
        }

    @Test
    fun encryptedNames_doNotRevealNamesOrMatchAcrossKeysAndFiles() =
        runBlocking {
            val storage = SecureStorageImpl(context, config)

            storage.putString(SHARED_NAME, VALUE)
            storage.saveBlob(SHARED_NAME, BLOB_CONTENT)

            val storedKey = values.all.keys.single()
            val storedFileName = blobDirectory.list()!!.single()
            assertFalse(storedKey.contains(SHARED_NAME))
            assertFalse(storedFileName.contains(SHARED_NAME))
            assertTrue(storedKey != storedFileName)
        }

    @Test
    fun entriesStoredUnderRandomizedNames_areRemovedOnOpen() =
        runBlocking {
            AeadConfig.register()
            val legacyNames =
                AndroidKeysetManager.Builder()
                    .withSharedPref(KeysetStorageContext(deviceProtectedContext), "secure_storage_metadata_keyset_pref_$namespace", legacyKeyset)
                    .withKeyTemplate(AeadKeyTemplates.AES256_GCM)
                    .withMasterKeyUri("android-keystore://secure_store_master_key_$namespace")
                    .build()
                    .keysetHandle
                    .getPrimitive(Aead::class.java)
            val legacyKey = legacyNames.encryptName(KEY)
            values.edit().putString(legacyKey, "unreadable").putString(PLAIN_KEY, "written without key encryption").commit()
            blobDirectory.mkdirs()
            File(blobDirectory, legacyNames.encryptName(BLOB)).writeBytes(BLOB_CONTENT)

            val storage = SecureStorageImpl(context, config)

            assertEquals(emptySet<String>(), storage.getAllKeys())
            assertEquals(emptySet<String>(), storage.getAllBlobNames())
            assertFalse(values.contains(legacyKey))
            assertTrue(values.contains(PLAIN_KEY))
            assertFalse(File(deviceProtectedContext.dataDir, "shared_prefs/$legacyKeyset.xml").exists())
            storage.putString(KEY, VALUE)
            assertEquals(VALUE, storage.getString(KEY))
        }

    private fun Aead.encryptName(name: String) =
        Base64.encodeToString(encrypt(name.toByteArray(), null), Base64.NO_WRAP or Base64.URL_SAFE)

    @Serializable
    private data class Credentials(val user: String, val pin: Int)

    private companion object {
        const val KEY = "token"
        const val OBJECT_KEY = "credentials"
        const val PLAIN_KEY = "plain"
        const val SHARED_NAME = "same-name"
        const val VALUE = "secret-value"
        const val OTHER_VALUE = "other-value"
        const val BLOB = "certificate.bin"
        val BLOB_CONTENT = byteArrayOf(1, 2, 3)
        val OTHER_BLOB_CONTENT = byteArrayOf(4, 5, 6, 7)
    }
}
