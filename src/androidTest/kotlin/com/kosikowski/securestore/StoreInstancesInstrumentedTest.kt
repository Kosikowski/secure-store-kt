package com.kosikowski.securestore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class StoreInstancesInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>().applicationContext
    private val deviceProtectedContext: Context = context.createDeviceProtectedStorageContext()
    private val namespace = "store_instances_${System.nanoTime()}"
    private val resets: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())

    @After
    fun tearDown() =
        runBlocking {
            SecureStorageImpl(context, config()).reset()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("secure_store_master_key_$namespace")
        }

    @Test
    fun resetThroughAnotherInstance_doesNotBringBackDeletedValues() =
        runBlocking {
            val writer = SecureStorageImpl(context, config())
            writer.putString(KEY, VALUE)

            SecureStorageImpl(context, config()).reset()
            writer.putString(OTHER_KEY, OTHER_VALUE)

            reloadValuesFromDisk()
            val fresh = SecureStorageImpl(context, config())
            assertEquals(setOf(OTHER_KEY), fresh.getAllKeys())
            assertEquals(OTHER_VALUE, fresh.getString(OTHER_KEY))
        }

    @Test
    fun keysetLossHandledByANewInstance_olderInstanceUsesTheNewKeysets() =
        runBlocking {
            val older = SecureStorageImpl(context, config())
            older.putString(KEY, VALUE)
            older.saveBlob(BLOB, BLOB_CONTENT)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("secure_store_master_key_$namespace")

            assertNull(SecureStorageImpl(context, config()).getString(KEY))
            older.putString(OTHER_KEY, OTHER_VALUE)
            older.saveBlob(BLOB, OTHER_BLOB_CONTENT)

            reloadValuesFromDisk()
            val fresh = SecureStorageImpl(context, config())
            assertNull(fresh.getString(KEY))
            assertEquals(OTHER_VALUE, fresh.getString(OTHER_KEY))
            assertArrayEquals(OTHER_BLOB_CONTENT, fresh.readBlob(BLOB))
            assertEquals(1, resets.size)
        }

    @Test
    fun writesFromSeveralInstancesDuringResets_leaveOnlyReadableData() =
        runBlocking {
            val writers = List(WRITERS) { SecureStorageImpl(context, config()) }
            val resetter = SecureStorageImpl(context, config())
            val writes = AtomicInteger()
            val writesBetweenResets = WRITERS * WRITES / (RESETS + 1)

            val writing =
                writers.mapIndexed { writer, storage ->
                    launch(Dispatchers.IO) {
                        repeat(WRITES) { i ->
                            storage.putString("w${writer}_$i", "value $i")
                            storage.saveBlob("w${writer}_$i", byteArrayOf(i.toByte()))
                            writes.incrementAndGet()
                        }
                    }
                }
            val resetting =
                launch(Dispatchers.IO) {
                    repeat(RESETS) { reset ->
                        while (writes.get() < (reset + 1) * writesBetweenResets) delay(1)
                        resetter.reset()
                    }
                }
            (writing + resetting).joinAll()

            reloadValuesFromDisk()
            val fresh = SecureStorageImpl(context, config())
            val keys = fresh.getAllKeys()
            val blobs = fresh.getAllBlobNames()
            assertTrue("writes after the last reset are kept", keys.isNotEmpty() || blobs.isNotEmpty())
            keys.forEach { assertNotNull(it, fresh.getString(it)) }
            blobs.forEach { assertNotNull(it, fresh.readBlob(it)) }
            assertTrue(resets.isEmpty())
        }

    @Test
    fun concurrentSavesReadsAndDeletesOfOneBlob_neverReadAPartialWrite() =
        runBlocking {
            val storages = List(BLOB_WORKERS) { SecureStorageImpl(context, config()) }
            val payload = ByteArray(BLOB_SIZE) { it.toByte() }

            storages.map { storage ->
                launch(Dispatchers.IO) {
                    repeat(BLOB_ROUNDS) {
                        storage.saveBlob(BLOB, payload)
                        storage.readBlob(BLOB)?.let { assertArrayEquals(payload, it) }
                        storage.deleteBlob(BLOB)
                    }
                }
            }.joinAll()
        }

    @Test
    fun sameNamespaceWithAnotherMasterKeyAlias_isRejected() {
        SecureStorageImpl(context, config())

        assertThrows(IllegalArgumentException::class.java) {
            SecureStorageImpl(context, config().toBuilder().masterKeyAlias("another_master_key").build())
        }
    }

    @Test
    fun defaultAndHighSecurityPresets_keepTheirDataApart() =
        runBlocking {
            val default = SecureStorageImpl(context, SecureStoreConfig.DEFAULT)
            val highSecurity = SecureStorageImpl(context, SecureStoreConfig.HIGH_SECURITY)
            try {
                default.putString(KEY, VALUE)
                highSecurity.putString(KEY, OTHER_VALUE)

                assertEquals(VALUE, default.getString(KEY))
                assertEquals(OTHER_VALUE, highSecurity.getString(KEY))
                assertEquals(setOf(KEY), highSecurity.getAllKeys())
            } finally {
                default.removeString(KEY)
                highSecurity.reset()
            }
        }

    /** SharedPreferences are cached per process; moving the file away and back makes the next open read it from disk. */
    private fun reloadValuesFromDisk() {
        val name = "secure_storage_prefs_$namespace"
        assertTrue(context.moveSharedPreferencesFrom(deviceProtectedContext, name))
        assertTrue(deviceProtectedContext.moveSharedPreferencesFrom(context, name))
    }

    private fun config() =
        SecureStoreConfig.Builder()
            .namespace(namespace)
            .decryptionFailurePolicy(DecryptionFailurePolicy.THROW_EXCEPTION)
            .onKeysetReset { resets += it }
            .build()

    private companion object {
        const val KEY = "token"
        const val VALUE = "secret-value"
        const val OTHER_KEY = "other"
        const val OTHER_VALUE = "other-value"
        const val BLOB = "certificate.bin"
        val BLOB_CONTENT = byteArrayOf(1, 2, 3)
        val OTHER_BLOB_CONTENT = byteArrayOf(4, 5, 6)
        const val BLOB_WORKERS = 6
        const val BLOB_ROUNDS = 60
        const val BLOB_SIZE = 256 * 1024
        const val WRITERS = 3
        const val WRITES = 40
        const val RESETS = 15
    }
}
