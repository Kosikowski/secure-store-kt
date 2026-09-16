package com.kosikowski.securestore

import android.content.Context
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory

@RunWith(AndroidJUnit4::class)
class KeyProtectionInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>().applicationContext
    private val namespace = "key_protection_${System.nanoTime()}"
    private val masterKeyAlias = "secure_store_master_key_$namespace"

    @After
    fun tearDown() =
        runBlocking {
            SecureStorageImpl(context, config(KeyProtection.SOFTWARE)).reset()
            keyStore().deleteEntry(masterKeyAlias)
        }

    @Test
    fun isHardwareBacked_reportsWhereTheMasterKeyIsKept() =
        runBlocking {
            val storage = SecureStorageImpl(context, config(KeyProtection.SOFTWARE))
            assertFalse("no master key before the first operation", storage.getStoreInfo().isHardwareBacked)

            storage.putString(KEY, VALUE)

            assertEquals(masterKeyInSecureHardware(), storage.getStoreInfo().isHardwareBacked)
        }

    @Test
    fun hardwareRequired_isCheckedAgainstTheMasterKey() =
        runBlocking {
            val storage = SecureStorageImpl(context, config(KeyProtection.HARDWARE_REQUIRED))

            val result = runCatching { storage.putString(KEY, VALUE) }

            val inSecureHardware = masterKeyInSecureHardware()
            Log.i(TAG, "master key in secure hardware: $inSecureHardware")
            if (inSecureHardware) {
                assertTrue(result.exceptionOrNull().toString(), result.isSuccess)
                assertEquals(VALUE, storage.getString(KEY))
            } else {
                assertTrue(result.exceptionOrNull() is SecureStoreException.HardwareRequiredException)
            }
        }

    private fun masterKeyInSecureHardware(): Boolean {
        val key = keyStore().getKey(masterKeyAlias, null) as SecretKey
        val keyInfo = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            keyInfo.securityLevel != KeyProperties.SECURITY_LEVEL_SOFTWARE && keyInfo.securityLevel != KeyProperties.SECURITY_LEVEL_UNKNOWN
        } else {
            @Suppress("DEPRECATION")
            keyInfo.isInsideSecureHardware
        }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun config(protection: KeyProtection) =
        SecureStoreConfig.Builder()
            .namespace(namespace)
            .keyProtection(protection)
            .build()

    private companion object {
        const val TAG = "KeyProtectionTest"
        const val KEY = "token"
        const val VALUE = "secret-value"
    }
}
