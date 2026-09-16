package com.kosikowski.securestore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class KeysetLossInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>().applicationContext
    private val namespace = "keyset_loss_${System.nanoTime()}"
    private val resets: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())

    @After
    fun tearDown() =
        runBlocking {
            for (mode in StorageMode.values()) {
                SecureStorageImpl(context, config(KeysetLossPolicy.RESET, mode)).reset()
            }
            keyStore().deleteEntry(masterKeyAlias)
        }

    @Test
    fun lostMasterKey_resetPolicy_discardsUnreadableDataAndKeepsWorking() =
        runBlocking {
            storeValueAndBlob(KeysetLossPolicy.RESET)
            keyStore().deleteEntry(masterKeyAlias)

            val storage = SecureStorageImpl(context, config(KeysetLossPolicy.RESET))

            assertNull(storage.getString(KEY))
            assertNull(storage.readBlob(BLOB))
            assertFalse(storage.blobExists(BLOB))
            assertEquals(1, resets.size)

            storage.putString(KEY, NEW_VALUE)
            storage.saveBlob(BLOB, NEW_BLOB_CONTENT)
            val reopened = SecureStorageImpl(context, config(KeysetLossPolicy.RESET))
            assertEquals(NEW_VALUE, reopened.getString(KEY))
            assertArrayEquals(NEW_BLOB_CONTENT, reopened.readBlob(BLOB))
            assertEquals(1, resets.size)
        }

    @Test
    fun lostMasterKey_throwPolicy_throwsUntilReset() =
        runBlocking {
            storeValueAndBlob(KeysetLossPolicy.THROW)
            keyStore().deleteEntry(masterKeyAlias)

            val storage =
                SecureStorageImpl(context, config(KeysetLossPolicy.THROW, decryptionFailurePolicy = DecryptionFailurePolicy.RETURN_NULL))

            assertKeysetLost { storage.getString(KEY) }
            assertKeysetLost { storage.putString(KEY, NEW_VALUE) }
            assertKeysetLost { storage.removeString(KEY) }
            assertKeysetLost { storage.contains(KEY) }
            assertKeysetLost { storage.getAllKeys() }
            assertKeysetLost { storage.readBlob(BLOB) }
            assertKeysetLost { storage.saveBlob(BLOB, NEW_BLOB_CONTENT) }
            assertKeysetLost { storage.blobExists(BLOB) }
            assertKeysetLost { storage.deleteBlob(BLOB) }
            assertKeysetLost { storage.getAllBlobNames() }
            assertKeysetLost { storage.clearAll() }

            storage.reset()

            assertNull(storage.getString(KEY))
            assertFalse(storage.blobExists(BLOB))
            storage.putString(KEY, NEW_VALUE)
            assertEquals(NEW_VALUE, storage.getString(KEY))
            assertTrue(resets.isEmpty())
        }

    @Test
    fun keysetThatNoLongerParses_resetPolicy_recovers() = assertResetRecoversFrom(corruptKeyset = "0a0b0c0d0e0f")

    @Test
    fun keysetThatIsNotHex_resetPolicy_recovers() = assertResetRecoversFrom(corruptKeyset = "not hex")

    @Test
    fun keysetThatIsNotHex_throwPolicy_throwsKeysetLost() =
        runBlocking {
            storeValueAndBlob(KeysetLossPolicy.THROW)
            corruptPrefsKeyset("not hex")

            val storage = SecureStorageImpl(context, config(KeysetLossPolicy.THROW))

            assertKeysetLost { storage.getString(KEY) }
        }

    @Test
    fun reset_deletesDataAndKeepsWorking() =
        runBlocking {
            val storage = SecureStorageImpl(context, config(KeysetLossPolicy.RESET))
            storage.putString(KEY, VALUE)
            storage.saveBlob(BLOB, BLOB_CONTENT)

            storage.reset()

            assertNull(storage.getString(KEY))
            assertFalse(storage.blobExists(BLOB))
            storage.putString(KEY, NEW_VALUE)
            assertEquals(NEW_VALUE, SecureStorageImpl(context, config(KeysetLossPolicy.RESET)).getString(KEY))
            assertTrue(resets.isEmpty())
        }

    private fun assertResetRecoversFrom(corruptKeyset: String) =
        runBlocking {
            storeValueAndBlob(KeysetLossPolicy.RESET)
            corruptPrefsKeyset(corruptKeyset)

            val storage = SecureStorageImpl(context, config(KeysetLossPolicy.RESET))

            assertNull(storage.getString(KEY))
            assertFalse(storage.blobExists(BLOB))
            assertEquals(1, resets.size)
            storage.putString(KEY, NEW_VALUE)
            assertEquals(NEW_VALUE, storage.getString(KEY))
        }

    private fun corruptPrefsKeyset(value: String) {
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences("secure_storage_prefs_key_$namespace", Context.MODE_PRIVATE)
            .edit()
            .putString("secure_storage_prefs_keyset_pref_$namespace", value)
            .commit()
    }

    private suspend fun storeValueAndBlob(policy: KeysetLossPolicy) {
        SecureStorageImpl(context, config(policy)).apply {
            putString(KEY, VALUE)
            saveBlob(BLOB, BLOB_CONTENT)
            assertEquals(VALUE, getString(KEY))
        }
    }

    private suspend fun assertKeysetLost(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected KeysetLostException")
        } catch (_: SecureStoreException.KeysetLostException) {
        }
    }

    private val masterKeyAlias get() = "secure_store_master_key_$namespace"

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun config(
        policy: KeysetLossPolicy,
        mode: StorageMode = StorageMode.DEVICE_PROTECTED,
        decryptionFailurePolicy: DecryptionFailurePolicy = DecryptionFailurePolicy.THROW_EXCEPTION,
    ) = SecureStoreConfig.Builder()
        .storageMode(mode)
        .namespace(namespace)
        .decryptionFailurePolicy(decryptionFailurePolicy)
        .keysetLossPolicy(policy)
        .onKeysetReset { resets += it }
        .build()

    private companion object {
        const val KEY = "token"
        const val VALUE = "secret-value"
        const val NEW_VALUE = "new-secret-value"
        const val BLOB = "certificate.bin"
        val BLOB_CONTENT = byteArrayOf(1, 2, 3, 4, 5)
        val NEW_BLOB_CONTENT = byteArrayOf(6, 7, 8)
    }
}
