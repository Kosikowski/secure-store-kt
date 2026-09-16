package com.kosikowski.securestore

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class KeyRotationInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>().applicationContext
    private val deviceProtectedContext: Context = context.createDeviceProtectedStorageContext()
    private val namespace = "key_rotation_${System.nanoTime()}"
    private val config =
        SecureStoreConfig.Builder()
            .namespace(namespace)
            .decryptionFailurePolicy(DecryptionFailurePolicy.THROW_EXCEPTION)
            .build()

    @After
    fun tearDown() =
        runBlocking {
            SecureStorageImpl(context, config).reset()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("secure_store_master_key_$namespace")
        }

    @Test
    fun rotateKeys_keepsExistingDataReadableAndWritesWithTheNewKey() =
        runBlocking {
            val storage = SecureStorageImpl(context, config)
            val otherInstance = SecureStorageImpl(context, config)
            storage.putString(KEY, VALUE)
            storage.saveBlob(BLOB, CONTENT)
            val primaryBefore = prefsKeysetInfo().primaryKeyId

            storage.rotateKeys()

            val keysetInfo = prefsKeysetInfo()
            assertEquals(2, keysetInfo.keyInfoCount)
            assertNotEquals(primaryBefore, keysetInfo.primaryKeyId)
            assertEquals(VALUE, SecureStorageImpl(context, config).getString(KEY))
            assertArrayEquals(CONTENT, SecureStorageImpl(context, config).readBlob(BLOB))

            otherInstance.putString(KEY, NEW_VALUE)
            assertEquals(keysetInfo.primaryKeyId, storedValueKeyId())
            assertEquals(NEW_VALUE, storage.getString(KEY))
        }

    private fun prefsKeysetInfo() =
        AndroidKeysetManager.Builder()
            .withSharedPref(KeysetStorageContext(deviceProtectedContext), "secure_storage_prefs_keyset_pref_$namespace", "secure_storage_prefs_key_$namespace")
            .withMasterKeyUri("android-keystore://secure_store_master_key_$namespace")
            .build()
            .keysetHandle
            .keysetInfo

    /** Tink prefixes ciphertext with a version byte and the id of the key that produced it. */
    private fun storedValueKeyId(): Int {
        val stored = deviceProtectedContext.getSharedPreferences("secure_storage_prefs_$namespace", Context.MODE_PRIVATE).getString(KEY, null)
        return ByteBuffer.wrap(Base64.decode(stored, Base64.DEFAULT), 1, 4).int
    }

    private companion object {
        const val KEY = "token"
        const val VALUE = "secret-value"
        const val NEW_VALUE = "new-secret-value"
        const val BLOB = "certificate.bin"
        val CONTENT = byteArrayOf(1, 2, 3)
    }
}
