package com.kosikowski.securestore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class BlobFilesInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext<Context>().applicationContext
    private val namespace = "blob_files_${System.nanoTime()}"
    private val config =
        SecureStoreConfig.Builder()
            .namespace(namespace)
            .decryptionFailurePolicy(DecryptionFailurePolicy.THROW_EXCEPTION)
            .build()
    private val partialDirectory = File(context.createDeviceProtectedStorageContext().filesDir, "secure_blobs_$namespace.partial")

    @After
    fun tearDown() =
        runBlocking {
            SecureStorageImpl(context, config).reset()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("secure_store_master_key_$namespace")
        }

    @Test
    fun savingABlob_leavesNoPartialFileBehind() =
        runBlocking {
            val storage = SecureStorageImpl(context, config)

            storage.saveBlob(BLOB, CONTENT)
            storage.saveBlob(BLOB, NEW_CONTENT)

            assertArrayEquals(NEW_CONTENT, storage.readBlob(BLOB))
            assertTrue(partialDirectory.list().isNullOrEmpty())
        }

    @Test
    fun writeInterruptedBeforeTheRename_leavesTheSavedBlobReadable() =
        runBlocking {
            SecureStorageImpl(context, config).saveBlob(BLOB, CONTENT)
            // What a crash part-way through the next save of the blob leaves behind.
            File(partialDirectory, BLOB).writeBytes(byteArrayOf(9, 9))

            val storage = SecureStorageImpl(context, config)

            assertArrayEquals(CONTENT, storage.readBlob(BLOB))
            assertEquals(setOf(BLOB), storage.getAllBlobNames())
            storage.saveBlob(BLOB, NEW_CONTENT)
            assertArrayEquals(NEW_CONTENT, storage.readBlob(BLOB))
        }

    @Test
    fun clearAll_removesPartialFiles() =
        runBlocking {
            val storage = SecureStorageImpl(context, config)
            storage.saveBlob(BLOB, CONTENT)
            File(partialDirectory, BLOB).writeBytes(byteArrayOf(9, 9))

            storage.clearAll()

            assertTrue(partialDirectory.list().isNullOrEmpty())
            assertEquals(emptySet<String>(), storage.getAllBlobNames())
        }

    private companion object {
        const val BLOB = "certificate.bin"
        val CONTENT = byteArrayOf(1, 2, 3)
        val NEW_CONTENT = byteArrayOf(4, 5, 6, 7)
    }
}
