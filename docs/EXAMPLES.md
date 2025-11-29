# Sample Usage Examples

This document contains example code showing how to use SecureStore in various scenarios.

## Basic Example

```kotlin
import android.content.Context
import com.kosikowski.securestore.SecureStorage
import com.kosikowski.securestore.SecureStorageImpl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

class ExampleUsage(context: Context) {
    private val storage: SecureStorage = SecureStorageImpl(context)
    
    fun basicStringStorage() = runBlocking {
        // Store
        storage.putString("api_key", "sk-1234567890")
        
        // Check if exists
        if (storage.contains("api_key")) {
            // Retrieve
            val apiKey = storage.getString("api_key")
            println("API Key: $apiKey")
        }
        
        // Remove
        storage.removeString("api_key")
    }
    
    fun objectStorage() = runBlocking {
        @Serializable
        data class User(val id: Int, val name: String, val email: String)
        
        val user = User(1, "John Doe", "john@example.com")
        
        // Store
        storage.putObject("current_user", user, User.serializer())
        
        // Retrieve
        val retrieved = storage.getObject("current_user", User.serializer())
        println("User: $retrieved")
        
        // Remove
        storage.removeObject("current_user")
    }
    
    fun blobStorage() = runBlocking {
        // Store binary data (e.g., encrypted file, certificate)
        val data = "Sensitive document content".toByteArray()
        storage.saveBlob("document.enc", data)
        
        // Check if exists
        if (storage.blobExists("document.enc")) {
            // Retrieve
            val retrieved = storage.readBlob("document.enc")
            println("Document size: ${retrieved?.size} bytes")
        }
        
        // Delete
        storage.deleteBlob("document.enc")
    }
    
    fun listStoredData() = runBlocking {
        // List all stored keys
        val allKeys = storage.getAllKeys()
        println("Stored keys: $allKeys")
        
        // List all blob filenames
        val allBlobs = storage.getAllBlobNames()
        println("Stored blobs: $allBlobs")
    }
    
    fun getStoreMetadata() {
        val info = storage.getStoreInfo()
        println("Encryption: ${info.encryptionAlgorithm}")
        println("Hardware-backed: ${info.isHardwareBacked}")
        println("Namespace: ${info.namespace}")
    }
}
```

## Configuration Examples

### Default Configuration

```kotlin
import com.kosikowski.securestore.SecureStorageImpl

// Uses SecureStoreConfig.DEFAULT
val storage = SecureStorageImpl(context)
```

### Custom Configuration

```kotlin
import com.kosikowski.securestore.*

val config = SecureStoreConfig.Builder()
    .encryption(EncryptionAlgorithm.AES_256_GCM)
    .keyProtection(KeyProtection.HARDWARE_PREFERRED)
    .storageMode(StorageMode.DEVICE_PROTECTED)
    .namespace("my_app")
    .encryptKeys(true)
    .encryptFileNames(true)
    .useAssociatedData(true)
    .secureMemory(true)
    .decryptionFailurePolicy(DecryptionFailurePolicy.DELETE_AND_RETURN_NULL)
    .build()

val storage = SecureStorageImpl(context, config)
```

### Using Presets

```kotlin
import com.kosikowski.securestore.*

// High security preset - hardware-backed keys, encrypted metadata
val highSecurityStorage = SecureStorageImpl(context, SecureStoreConfig.HIGH_SECURITY)

// Performance preset - ChaCha20-Poly1305, optimized for speed
val performanceStorage = SecureStorageImpl(context, SecureStoreConfig.PERFORMANCE)

// Default preset
val defaultStorage = SecureStorageImpl(context, SecureStoreConfig.DEFAULT)
```

### Namespace Isolation

```kotlin
import com.kosikowski.securestore.*

// Create isolated storage instances
val userDataStorage = SecureStorageImpl(context, SecureStoreConfig.Builder()
    .namespace("user_data")
    .build())

val cacheStorage = SecureStorageImpl(context, SecureStoreConfig.Builder()
    .namespace("cache")
    .build())

// Data in different namespaces is completely isolated
runBlocking {
    userDataStorage.putString("key", "user value")
    cacheStorage.putString("key", "cache value")
    
    println(userDataStorage.getString("key")) // "user value"
    println(cacheStorage.getString("key"))    // "cache value"
}
```

### ChaCha20-Poly1305 for Performance

```kotlin
import com.kosikowski.securestore.*

// ChaCha20-Poly1305 is faster on devices without AES hardware acceleration
val config = SecureStoreConfig.Builder()
    .encryption(EncryptionAlgorithm.CHACHA20_POLY1305)
    .build()

val storage = SecureStorageImpl(context, config)
```

## Authentication Example

```kotlin
import com.kosikowski.securestore.*
import kotlinx.serialization.Serializable

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)

class AuthManager(context: Context) {
    
    private val storage: SecureStorage = SecureStorageImpl(
        context,
        SecureStoreConfig.Builder()
            .namespace("auth")
            .keyProtection(KeyProtection.HARDWARE_PREFERRED)
            .build()
    )
    
    suspend fun saveTokens(tokens: AuthTokens) {
        storage.putObject("auth_tokens", tokens, AuthTokens.serializer())
    }
    
    suspend fun getTokens(): AuthTokens? {
        return storage.getObject("auth_tokens", AuthTokens.serializer())
    }
    
    suspend fun clearTokens() {
        storage.removeObject("auth_tokens")
    }
    
    suspend fun hasTokens(): Boolean {
        return storage.contains("auth_tokens")
    }
    
    suspend fun isAuthenticated(): Boolean {
        val tokens = getTokens() ?: return false
        return System.currentTimeMillis() < tokens.expiresAt
    }
}
```

## Error Handling Example

```kotlin
import com.kosikowski.securestore.*
import timber.log.Timber

class SecureDataManager(context: Context) {
    
    private val storage: SecureStorage = SecureStorageImpl(
        context,
        SecureStoreConfig.Builder()
            .decryptionFailurePolicy(DecryptionFailurePolicy.THROW_EXCEPTION)
            .build()
    )
    
    suspend fun saveSecurely(key: String, value: String): Result<Unit> {
        return try {
            storage.putString(key, value)
            Result.success(Unit)
        } catch (e: SecureStoreException.EncryptionException) {
            Timber.e(e, "Encryption failed for $key")
            Result.failure(e)
        } catch (e: SecureStoreException.StorageException) {
            Timber.e(e, "Storage failed for $key")
            Result.failure(e)
        } catch (e: SecureStoreException) {
            Timber.e(e, "SecureStore error for $key")
            Result.failure(e)
        }
    }
    
    suspend fun getSecurely(key: String): Result<String?> {
        return try {
            val value = storage.getString(key)
            Result.success(value)
        } catch (e: SecureStoreException.DecryptionException) {
            Timber.e(e, "Decryption failed for $key")
            Result.failure(e)
        } catch (e: SecureStoreException) {
            Timber.e(e, "SecureStore error for $key")
            Result.failure(e)
        }
    }
}
```

## Dependency Injection Examples

### Hilt

```kotlin
import android.content.Context
import com.kosikowski.securestore.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    
    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage = SecureStorageImpl(context)
    
    @Provides
    @Singleton
    @Named("high_security")
    fun provideHighSecurityStorage(
        @ApplicationContext context: Context
    ): SecureStorage = SecureStorageImpl(context, SecureStoreConfig.HIGH_SECURITY)
    
    @Provides
    @Singleton
    @Named("auth")
    fun provideAuthStorage(
        @ApplicationContext context: Context
    ): SecureStorage = SecureStorageImpl(
        context,
        SecureStoreConfig.Builder()
            .namespace("auth")
            .keyProtection(KeyProtection.HARDWARE_PREFERRED)
            .build()
    )
}

// Usage in ViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val storage: SecureStorage,
    @Named("auth") private val authStorage: SecureStorage
) : ViewModel() {
    
    fun saveUserPreferences(prefs: UserPreferences) {
        viewModelScope.launch {
            storage.putObject("user_prefs", prefs, UserPreferences.serializer())
        }
    }
}
```

### Koin

```kotlin
import com.kosikowski.securestore.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val storageModule = module {
    single<SecureStorage> { SecureStorageImpl(androidContext()) }
    
    single<SecureStorage>(named("high_security")) {
        SecureStorageImpl(androidContext(), SecureStoreConfig.HIGH_SECURITY)
    }
    
    single<SecureStorage>(named("auth")) {
        SecureStorageImpl(
            androidContext(),
            SecureStoreConfig.Builder()
                .namespace("auth")
                .keyProtection(KeyProtection.HARDWARE_PREFERRED)
                .build()
        )
    }
}

class UserRepository(private val storage: SecureStorage) {
    suspend fun saveUser(user: User) {
        storage.putObject("user", user, User.serializer())
    }
}

val repositoryModule = module {
    single { UserRepository(get()) }
}
```

## Testing

```kotlin
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kosikowski.securestore.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureStorageTest {
    
    private lateinit var storage: SecureStorage
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Use a test-specific namespace to avoid conflicts
        val config = SecureStoreConfig.Builder()
            .namespace("test_${System.currentTimeMillis()}")
            .build()
        storage = SecureStorageImpl(context, config)
        runBlocking { storage.clearAll() }
    }
    
    @After
    fun teardown() {
        runBlocking { storage.clearAll() }
    }
    
    @Test
    fun testBasicStorage() = runBlocking {
        storage.putString("test", "value")
        assertEquals("value", storage.getString("test"))
        assertTrue(storage.contains("test"))
    }
    
    @Test
    fun testContains() = runBlocking {
        assertFalse(storage.contains("nonexistent"))
        storage.putString("exists", "value")
        assertTrue(storage.contains("exists"))
    }
    
    @Test
    fun testBlobExists() = runBlocking {
        assertFalse(storage.blobExists("nonexistent"))
        storage.saveBlob("exists", "data".toByteArray())
        assertTrue(storage.blobExists("exists"))
    }
    
    @Test
    fun testGetAllKeys() = runBlocking {
        storage.putString("key1", "value1")
        storage.putString("key2", "value2")
        
        val keys = storage.getAllKeys()
        assertEquals(2, keys.size)
        assertTrue(keys.contains("key1"))
        assertTrue(keys.contains("key2"))
    }
    
    @Test
    fun testStoreInfo() {
        val info = storage.getStoreInfo()
        assertEquals("AES_256_GCM", info.encryptionAlgorithm)
        assertFalse(info.keyEncryptionEnabled)
    }
}
```

## Full App Example

```kotlin
import android.app.Application
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kosikowski.securestore.*
import kotlinx.coroutines.launch

class MyApplication : Application() {
    lateinit var secureStorage: SecureStorage
        private set
    
    lateinit var authStorage: SecureStorage
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        // General purpose storage
        secureStorage = SecureStorageImpl(this)
        
        // High-security storage for authentication
        authStorage = SecureStorageImpl(
            this,
            SecureStoreConfig.Builder()
                .namespace("auth")
                .keyProtection(KeyProtection.HARDWARE_PREFERRED)
                .encryptKeys(true)
                .build()
        )
    }
}

class LoginActivity : AppCompatActivity() {
    
    private val authStorage by lazy {
        (application as MyApplication).authStorage
    }
    
    private fun onLoginSuccess(username: String, token: String) {
        lifecycleScope.launch {
            try {
                authStorage.putString("username", username)
                authStorage.putString("auth_token", token)
                navigateToHome()
            } catch (e: SecureStoreException) {
                showError("Failed to save credentials")
            }
        }
    }
    
    private fun checkExistingSession() {
        lifecycleScope.launch {
            if (authStorage.contains("auth_token")) {
                navigateToHome()
            }
        }
    }
    
    private fun onLogout() {
        lifecycleScope.launch {
            authStorage.clearAll()
            navigateToLogin()
        }
    }
    
    private fun navigateToHome() { /* Navigation logic */ }
    private fun navigateToLogin() { /* Navigation logic */ }
    private fun showError(message: String) { /* Error display logic */ }
}
```

## Kotlin Flow Integration

```kotlin
import com.kosikowski.securestore.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurePreferencesManager(private val storage: SecureStorage) {
    
    private val _authTokenFlow = MutableStateFlow<String?>(null)
    val authTokenFlow: StateFlow<String?> = _authTokenFlow.asStateFlow()
    
    private val _isAuthenticatedFlow = MutableStateFlow(false)
    val isAuthenticatedFlow: StateFlow<Boolean> = _isAuthenticatedFlow.asStateFlow()
    
    suspend fun init() {
        val hasToken = storage.contains("auth_token")
        _isAuthenticatedFlow.value = hasToken
        if (hasToken) {
            _authTokenFlow.value = storage.getString("auth_token")
        }
    }
    
    suspend fun setAuthToken(token: String) {
        storage.putString("auth_token", token)
        _authTokenFlow.value = token
        _isAuthenticatedFlow.value = true
    }
    
    suspend fun clearAuthToken() {
        storage.removeString("auth_token")
        _authTokenFlow.value = null
        _isAuthenticatedFlow.value = false
    }
}
```

## Background Work with WorkManager

```kotlin
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kosikowski.securestore.*
import kotlinx.serialization.Serializable

class DataSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val storage = SecureStorageImpl(
        context,
        SecureStoreConfig.Builder()
            .namespace("sync")
            .build()
    )
    
    override suspend fun doWork(): Result {
        return try {
            // Check for auth token
            if (!storage.contains("auth_token")) {
                return Result.failure()
            }
            
            val token = storage.getString("auth_token")!!
            
            // Sync data...
            val syncedData = fetchDataFromApi(token)
            
            // Store synced data
            storage.putObject("synced_data", syncedData, SyncedData.serializer())
            
            Result.success()
        } catch (e: SecureStoreException) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure()
        }
    }
    
    private suspend fun fetchDataFromApi(token: String): SyncedData {
        // API call implementation
        return SyncedData(emptyList())
    }
}

@Serializable
data class SyncedData(val items: List<String>)
```

## Encrypted File Manager with Listing

```kotlin
import com.kosikowski.securestore.*
import java.io.File

class SecureFileManager(context: Context) {
    
    private val storage: SecureStorage = SecureStorageImpl(
        context,
        SecureStoreConfig.Builder()
            .namespace("files")
            .encryptFileNames(true)
            .build()
    )
    
    suspend fun saveEncryptedFile(file: File): Boolean {
        return try {
            val bytes = file.readBytes()
            storage.saveBlob(file.name, bytes)
            true
        } catch (e: SecureStoreException) {
            false
        }
    }
    
    suspend fun readEncryptedFile(fileName: String): ByteArray? {
        return storage.readBlob(fileName)
    }
    
    suspend fun deleteEncryptedFile(fileName: String): Boolean {
        return storage.deleteBlob(fileName)
    }
    
    suspend fun fileExists(fileName: String): Boolean {
        return storage.blobExists(fileName)
    }
    
    suspend fun listEncryptedFiles(): Set<String> {
        return storage.getAllBlobNames()
    }
}
```

## Multi-Account Support with Namespaces

```kotlin
import com.kosikowski.securestore.*
import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String,
    val username: String,
    val token: String
)

class MultiAccountManager(private val context: Context) {
    
    private val accountListStorage = SecureStorageImpl(
        context,
        SecureStoreConfig.Builder()
            .namespace("account_list")
            .build()
    )
    
    private fun getAccountStorage(accountId: String): SecureStorage {
        return SecureStorageImpl(
            context,
            SecureStoreConfig.Builder()
                .namespace("account_$accountId")
                .keyProtection(KeyProtection.HARDWARE_PREFERRED)
                .build()
        )
    }
    
    suspend fun addAccount(account: Account) {
        // Store account in the account-specific namespace
        val accountStorage = getAccountStorage(account.id)
        accountStorage.putObject("account", account, Account.serializer())
        
        // Add to account list
        val accountIds = getAccountIds().toMutableSet()
        accountIds.add(account.id)
        accountListStorage.putString("account_ids", accountIds.joinToString(","))
    }
    
    suspend fun getAccountIds(): Set<String> {
        val ids = accountListStorage.getString("account_ids") ?: return emptySet()
        return ids.split(",").filter { it.isNotBlank() }.toSet()
    }
    
    suspend fun getAccount(accountId: String): Account? {
        val accountStorage = getAccountStorage(accountId)
        return accountStorage.getObject("account", Account.serializer())
    }
    
    suspend fun setActiveAccount(accountId: String) {
        accountListStorage.putString("active_account_id", accountId)
    }
    
    suspend fun getActiveAccount(): Account? {
        val accountId = accountListStorage.getString("active_account_id") ?: return null
        return getAccount(accountId)
    }
    
    suspend fun removeAccount(accountId: String) {
        // Clear account-specific storage
        val accountStorage = getAccountStorage(accountId)
        accountStorage.clearAll()
        
        // Remove from account list
        val accountIds = getAccountIds().toMutableSet()
        accountIds.remove(accountId)
        accountListStorage.putString("account_ids", accountIds.joinToString(","))
    }
}
```

## Session Management with Expiry

```kotlin
import com.kosikowski.securestore.*
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val deviceId: String
)

class SessionManager(context: Context) {
    
    private val storage: SecureStorage = SecureStorageImpl(
        context,
        SecureStoreConfig.Builder()
            .namespace("session")
            .keyProtection(KeyProtection.HARDWARE_PREFERRED)
            .encryptKeys(true)
            .build()
    )
    
    suspend fun createSession(session: Session) {
        storage.putObject("session", session, Session.serializer())
    }
    
    suspend fun getSession(): Session? {
        return storage.getObject("session", Session.serializer())
    }
    
    suspend fun hasSession(): Boolean {
        return storage.contains("session")
    }
    
    suspend fun isSessionValid(): Boolean {
        val session = getSession() ?: return false
        return System.currentTimeMillis() < session.expiresAt
    }
    
    suspend fun refreshSession(newAccessToken: String, newExpiresAt: Long) {
        val currentSession = getSession() ?: return
        val updatedSession = currentSession.copy(
            accessToken = newAccessToken,
            expiresAt = newExpiresAt
        )
        createSession(updatedSession)
    }
    
    suspend fun clearSession() {
        storage.removeObject("session")
    }
    
    fun getSessionInfo(): SecureStoreInfo {
        return storage.getStoreInfo()
    }
}
```
