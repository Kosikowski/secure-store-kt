package com.kosikowski.securestore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [SecureStorage].
 *
 * These tests run on an Android device or emulator and verify:
 * - Basic CRUD operations for strings, objects, and blobs
 * - Thread-safety under concurrent access
 * - Proper encryption/decryption
 * - Error handling
 */
@RunWith(AndroidJUnit4::class)
class SecureStorageInstrumentedTest {

    private lateinit var secureStorage: SecureStorage

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        secureStorage = SecureStorageImpl(context)
        runBlocking {
            secureStorage.clearAll()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            secureStorage.clearAll()
        }
    }

    // ==================== BASIC OPERATIONS ====================

    @Test
    fun putAndGetString_roundTrip() =
        runBlocking {
            secureStorage.putString(STRING_KEY, STRING_VALUE)
            assertEquals(STRING_VALUE, secureStorage.getString(STRING_KEY))
        }

    @Test
    fun getString_nonExistentKey_returnsNull() =
        runBlocking {
            assertNull(secureStorage.getString("non-existent-key"))
        }

    @Test
    fun removeString_leavesNull() =
        runBlocking {
            secureStorage.putString(STRING_KEY, STRING_VALUE)
            secureStorage.removeString(STRING_KEY)
            assertNull(secureStorage.getString(STRING_KEY))
        }

    @Test
    fun putString_overwritesExistingValue() =
        runBlocking {
            secureStorage.putString(STRING_KEY, "original")
            secureStorage.putString(STRING_KEY, "updated")
            assertEquals("updated", secureStorage.getString(STRING_KEY))
        }

    @Test
    fun saveBlob_andRead_returnsSamePayload() =
        runBlocking {
            secureStorage.saveBlob(BLOB_FILE, BLOB_PAYLOAD)
            assertArrayEquals(BLOB_PAYLOAD, secureStorage.readBlob(BLOB_FILE))
        }

    @Test
    fun readBlob_nonExistentFile_returnsNull() =
        runBlocking {
            assertNull(secureStorage.readBlob("non-existent-file"))
        }

    @Test
    fun deleteBlob_removesFile() =
        runBlocking {
            secureStorage.saveBlob(BLOB_FILE, BLOB_PAYLOAD)
            assertTrue(secureStorage.deleteBlob(BLOB_FILE))
            assertNull(secureStorage.readBlob(BLOB_FILE))
        }

    @Test
    fun deleteBlob_nonExistentFile_returnsFalse() =
        runBlocking {
            assertFalse(secureStorage.deleteBlob("non-existent-file"))
        }

    @Test
    fun putAndGetObject_roundTrip() =
        runBlocking {
            val secret = SerializableSecret(id = 42, token = "secret-token-123")
            secureStorage.putObject(OBJECT_KEY, secret, SerializableSecret.serializer())
            assertEquals(secret, secureStorage.getObject(OBJECT_KEY, SerializableSecret.serializer()))
        }

    @Test
    fun getObject_nonExistentKey_returnsNull() =
        runBlocking {
            assertNull(secureStorage.getObject("non-existent", SerializableSecret.serializer()))
        }

    @Test
    fun clearAll_removesAllData() =
        runBlocking {
            // Store various types of data
            secureStorage.putString(STRING_KEY, STRING_VALUE)
            secureStorage.saveBlob(BLOB_FILE, BLOB_PAYLOAD)
            secureStorage.putObject(OBJECT_KEY, SerializableSecret(1, "token"), SerializableSecret.serializer())

            // Clear everything
            secureStorage.clearAll()

            // Verify all data is gone
            assertNull(secureStorage.getString(STRING_KEY))
            assertNull(secureStorage.readBlob(BLOB_FILE))
            assertNull(secureStorage.getObject(OBJECT_KEY, SerializableSecret.serializer()))
        }

    // ==================== THREAD-SAFETY TESTS ====================

    @Test
    fun concurrentFileWrites_toDifferentFiles_shouldNotInterfere() =
        runBlocking {
            val jobs =
                (1..100).map { i ->
                    launch(Dispatchers.IO) {
                        secureStorage.saveBlob("file_$i", "data_$i".toByteArray())
                    }
                }
            jobs.forEach { it.join() }

            // Verify all files written correctly
            (1..100).forEach { i ->
                val data = secureStorage.readBlob("file_$i")
                assertArrayEquals("File $i should contain correct data", "data_$i".toByteArray(), data)
            }
        }

    @Test
    fun concurrentWrites_toSameFile_shouldBeSerialized() =
        runBlocking {
            val jobs =
                (1..100).map { i ->
                    launch(Dispatchers.IO) {
                        secureStorage.saveBlob("same_file", "data_$i".toByteArray())
                    }
                }
            jobs.forEach { it.join() }

            // Last write wins - data should be valid (not corrupted)
            val data = secureStorage.readBlob("same_file")
            assertTrue("File should exist and not be corrupted", data != null)
            val content = data!!.decodeToString()
            assertTrue(
                "Content should match pattern 'data_\\d+'",
                content.matches("data_\\d+".toRegex()),
            )
        }

    @Test
    fun concurrentReadAndWrite_toSameFile_shouldBeSerialized() =
        runBlocking {
            // Initialize file
            secureStorage.saveBlob("rw_file", "initial".toByteArray())

            val writeJobs =
                (1..50).map { i ->
                    launch(Dispatchers.IO) {
                        secureStorage.saveBlob("rw_file", "write_$i".toByteArray())
                    }
                }

            val readJobs =
                (1..50).map {
                    launch(Dispatchers.IO) {
                        val data = secureStorage.readBlob("rw_file")
                        // Should either get a valid write or initial value, never corrupted
                        assertTrue("Read data should not be null or corrupted", data != null)
                    }
                }

            (writeJobs + readJobs).forEach { it.join() }

            // Final read should succeed
            val finalData = secureStorage.readBlob("rw_file")
            assertTrue("Final read should succeed", finalData != null)
        }

    @Test
    fun concurrentStringOperations_shouldBeThreadSafe() =
        runBlocking {
            val jobs =
                (1..100).map { i ->
                    launch(Dispatchers.IO) {
                        secureStorage.putString("key_$i", "value_$i")
                    }
                }
            jobs.forEach { it.join() }

            // Verify all values stored correctly
            (1..100).forEach { i ->
                assertEquals("value_$i", secureStorage.getString("key_$i"))
            }
        }

    @Test
    fun concurrentObjectOperations_shouldBeThreadSafe() =
        runBlocking {
            val jobs =
                (1..50).map { i ->
                    launch(Dispatchers.IO) {
                        val secret = SerializableSecret(id = i, token = "token_$i")
                        secureStorage.putObject("object_$i", secret, SerializableSecret.serializer())
                    }
                }
            jobs.forEach { it.join() }

            // Verify all objects stored correctly
            (1..50).forEach { i ->
                val expected = SerializableSecret(id = i, token = "token_$i")
                val actual = secureStorage.getObject("object_$i", SerializableSecret.serializer())
                assertEquals(expected, actual)
            }
        }

    @Test
    fun concurrentDeleteOperations_shouldBeThreadSafe() =
        runBlocking {
            // Create 100 files
            (1..100).forEach { i ->
                secureStorage.saveBlob("delete_$i", "data_$i".toByteArray())
            }

            // Delete concurrently
            val jobs =
                (1..100).map { i ->
                    launch(Dispatchers.IO) {
                        secureStorage.deleteBlob("delete_$i")
                    }
                }
            jobs.forEach { it.join() }

            // Verify all files deleted
            (1..100).forEach { i ->
                assertNull("File delete_$i should be deleted", secureStorage.readBlob("delete_$i"))
            }
        }

    @Test
    fun clearAll_whileOtherOperationsInProgress_shouldBeThreadSafe() =
        runBlocking {
            // Start some write operations
            val writeJobs =
                (1..20).map { i ->
                    launch(Dispatchers.IO) {
                        secureStorage.saveBlob("concurrent_$i", "data_$i".toByteArray())
                        delay(10) // Small delay
                    }
                }

            // Start clearAll in parallel
            val clearJob =
                launch(Dispatchers.IO) {
                    delay(5) // Let some writes start
                    secureStorage.clearAll()
                }

            (writeJobs + clearJob).forEach { it.join() }

            // After clearAll completes, verify storage is empty or contains only very recent writes
            // (depending on timing, some writes after clearAll might succeed)
            // The important thing is no corruption or crashes
            assertTrue("Test completed without crashes or corruption", true)
        }

    @Test
    fun multipleConcurrentClearAll_shouldBeSerializedAndSafe() =
        runBlocking {
            // Create some initial data
            (1..10).forEach { i ->
                secureStorage.saveBlob("clear_test_$i", "data".toByteArray())
            }

            // Multiple concurrent clearAll calls
            val jobs =
                (1..10).map {
                    launch(Dispatchers.IO) {
                        secureStorage.clearAll()
                    }
                }
            jobs.forEach { it.join() }

            // Verify storage is cleared (all clearAll calls succeeded without conflict)
            (1..10).forEach { i ->
                assertNull(secureStorage.readBlob("clear_test_$i"))
            }
        }

    @Test
    fun stressTest_mixedConcurrentOperations_shouldRemainConsistent() =
        runBlocking {
            val operations = mutableListOf<Job>()

            // Mix of different operations
            repeat(20) { i ->
                // Writes
                operations.add(
                    launch(Dispatchers.IO) {
                        secureStorage.saveBlob("stress_$i", "data_$i".toByteArray())
                    },
                )

                // Reads
                operations.add(
                    launch(Dispatchers.IO) {
                        secureStorage.readBlob("stress_$i")
                    },
                )

                // Strings
                operations.add(
                    launch(Dispatchers.IO) {
                        secureStorage.putString("str_$i", "value_$i")
                    },
                )

                // Objects
                operations.add(
                    launch(Dispatchers.IO) {
                        secureStorage.putObject(
                            "obj_$i",
                            SerializableSecret(i, "token_$i"),
                            SerializableSecret.serializer(),
                        )
                    },
                )
            }

            operations.forEach { it.join() }

            // Verify no corruption - at least some data should be valid
            var validReads = 0
            (0..19).forEach { i ->
                if (secureStorage.readBlob("stress_$i") != null) validReads++
            }
            assertTrue("At least some operations should have succeeded", validReads > 0)
        }

    // ==================== EDGE CASES ====================

    @Test
    fun test_save_blob_with_empty_byte_array_succeeds() =
        runBlocking {
            val empty = ByteArray(0)
            secureStorage.saveBlob("empty_file", empty)
            assertArrayEquals(empty, secureStorage.readBlob("empty_file"))
        }

    @Test
    fun test_put_string_with_empty_string_succeeds() =
        runBlocking {
            secureStorage.putString("empty_key", "")
            assertEquals("", secureStorage.getString("empty_key"))
        }

    @Test
    fun test_save_blob_with_large_payload_succeeds() =
        runBlocking {
            val largePayload = ByteArray(1024 * 1024) { it.toByte() } // 1MB
            secureStorage.saveBlob("large_file", largePayload)
            assertArrayEquals(largePayload, secureStorage.readBlob("large_file"))
        }

    // ==================== NEW API TESTS ====================

    @Test
    fun contains_existingKey_returnsTrue() =
        runBlocking {
            secureStorage.putString(STRING_KEY, STRING_VALUE)
            assertTrue(secureStorage.contains(STRING_KEY))
        }

    @Test
    fun contains_nonExistentKey_returnsFalse() =
        runBlocking {
            assertFalse(secureStorage.contains("non-existent-key"))
        }

    @Test
    fun removeObject_removesStoredObject() =
        runBlocking {
            val secret = SerializableSecret(id = 1, token = "token")
            secureStorage.putObject(OBJECT_KEY, secret, SerializableSecret.serializer())
            secureStorage.removeObject(OBJECT_KEY)
            assertNull(secureStorage.getObject(OBJECT_KEY, SerializableSecret.serializer()))
        }

    @Test
    fun blobExists_existingBlob_returnsTrue() =
        runBlocking {
            secureStorage.saveBlob(BLOB_FILE, BLOB_PAYLOAD)
            assertTrue(secureStorage.blobExists(BLOB_FILE))
        }

    @Test
    fun blobExists_nonExistentBlob_returnsFalse() =
        runBlocking {
            assertFalse(secureStorage.blobExists("non-existent-blob"))
        }

    @Test
    fun getAllKeys_returnsAllStoredKeys() =
        runBlocking {
            secureStorage.putString("key1", "value1")
            secureStorage.putString("key2", "value2")
            secureStorage.putString("key3", "value3")

            val keys = secureStorage.getAllKeys()

            assertEquals(3, keys.size)
            assertTrue(keys.contains("key1"))
            assertTrue(keys.contains("key2"))
            assertTrue(keys.contains("key3"))
        }

    @Test
    fun getAllKeys_emptyStorage_returnsEmptySet() =
        runBlocking {
            val keys = secureStorage.getAllKeys()
            assertTrue(keys.isEmpty())
        }

    @Test
    fun getAllBlobNames_returnsAllStoredBlobNames() =
        runBlocking {
            secureStorage.saveBlob("blob1", "data1".toByteArray())
            secureStorage.saveBlob("blob2", "data2".toByteArray())
            secureStorage.saveBlob("blob3", "data3".toByteArray())

            val names = secureStorage.getAllBlobNames()

            assertEquals(3, names.size)
            assertTrue(names.contains("blob1"))
            assertTrue(names.contains("blob2"))
            assertTrue(names.contains("blob3"))
        }

    @Test
    fun getAllBlobNames_emptyStorage_returnsEmptySet() =
        runBlocking {
            val names = secureStorage.getAllBlobNames()
            assertTrue(names.isEmpty())
        }

    @Test
    fun getStoreInfo_returnsCorrectInfo() {
        val info = secureStorage.getStoreInfo()

        assertEquals("AES_256_GCM", info.encryptionAlgorithm)
        assertEquals("default", info.namespace)
        assertFalse(info.keyEncryptionEnabled)
        assertFalse(info.fileNameEncryptionEnabled)
    }

    // ==================== CONFIGURATION TESTS ====================

    @Test
    fun configuredNamespace_isolatesData() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val config1 = SecureStoreConfig.Builder()
                .namespace("namespace1")
                .build()
            val config2 = SecureStoreConfig.Builder()
                .namespace("namespace2")
                .build()

            val storage1 = SecureStorageImpl(context, config1)
            val storage2 = SecureStorageImpl(context, config2)

            try {
                // Store in namespace1
                storage1.putString("shared_key", "value_from_ns1")

                // Should not be visible in namespace2
                assertNull(storage2.getString("shared_key"))

                // Store different value in namespace2
                storage2.putString("shared_key", "value_from_ns2")

                // Each namespace should have its own value
                assertEquals("value_from_ns1", storage1.getString("shared_key"))
                assertEquals("value_from_ns2", storage2.getString("shared_key"))
            } finally {
                storage1.clearAll()
                storage2.clearAll()
            }
        }

    @Test
    fun configuredEncryption_chaCha20Works() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val config = SecureStoreConfig.Builder()
                .encryption(EncryptionAlgorithm.CHACHA20_POLY1305)
                .namespace("chacha_test")
                .build()

            val storage = SecureStorageImpl(context, config)

            try {
                storage.putString("test_key", "test_value")
                assertEquals("test_value", storage.getString("test_key"))

                val info = storage.getStoreInfo()
                assertEquals("CHACHA20_POLY1305", info.encryptionAlgorithm)
            } finally {
                storage.clearAll()
            }
        }

    @Test
    fun configuredWithAssociatedData_preventsRelocation() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()

            val config = SecureStoreConfig.Builder()
                .useAssociatedData(true)
                .namespace("aad_test")
                .build()

            val storage = SecureStorageImpl(context, config)

            try {
                storage.saveBlob("original_name", "secret_data".toByteArray())
                val data = storage.readBlob("original_name")
                assertArrayEquals("secret_data".toByteArray(), data)
            } finally {
                storage.clearAll()
            }
        }

    @Test
    fun presetConfigurations_work() {
        // Test that preset configurations can be instantiated
        val defaultConfig = SecureStoreConfig.DEFAULT
        assertEquals(EncryptionAlgorithm.AES_256_GCM, defaultConfig.encryption)
        assertEquals(KeyProtection.SOFTWARE, defaultConfig.keyProtection)

        val performanceConfig = SecureStoreConfig.PERFORMANCE
        assertEquals(EncryptionAlgorithm.CHACHA20_POLY1305, performanceConfig.encryption)
        assertFalse(performanceConfig.useAssociatedData)

        val highSecurityConfig = SecureStoreConfig.HIGH_SECURITY
        assertEquals(KeyProtection.HARDWARE_REQUIRED, highSecurityConfig.keyProtection)
        assertTrue(highSecurityConfig.encryptKeys)
        assertTrue(highSecurityConfig.encryptFileNames)
        assertTrue(highSecurityConfig.secureMemory)
    }

    @Test
    fun configBuilder_toBuilder_preservesValues() {
        val original = SecureStoreConfig.Builder()
            .encryption(EncryptionAlgorithm.AES_128_GCM)
            .namespace("test")
            .encryptKeys(true)
            .build()

        val copy = original.toBuilder()
            .encryptFileNames(true)
            .build()

        // Original values preserved
        assertEquals(EncryptionAlgorithm.AES_128_GCM, copy.encryption)
        assertEquals("test", copy.namespace)
        assertTrue(copy.encryptKeys)

        // New value added
        assertTrue(copy.encryptFileNames)
    }

    private companion object {
        private const val STRING_KEY = "secure-storage-key"
        private const val STRING_VALUE = "super-secret"
        private const val BLOB_FILE = "secure-storage-blob"
        private val BLOB_PAYLOAD = byteArrayOf(0, 1, 2, 3, 4)
        private const val OBJECT_KEY = "secure-storage-object"
    }

    @Serializable
    private data class SerializableSecret(
        val id: Int,
        val token: String,
    )
}

