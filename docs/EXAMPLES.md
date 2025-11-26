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
        
        // Retrieve
        val apiKey = storage.getString("api_key")
        println("API Key: $apiKey")
        
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
    }
    
    fun blobStorage() = runBlocking {
        // Store binary data (e.g., encrypted file, certificate)
        val data = "Sensitive document content".toByteArray()
        storage.saveBlob("document.enc", data)
        
        // Retrieve
        val retrieved = storage.readBlob("document.enc")
        println("Document size: ${retrieved?.size} bytes")
        
        // Delete
        storage.deleteBlob("document.enc")
    }
}
```

## Authentication Example

```kotlin
import com.kosikowski.securestore.SecureStorage
import kotlinx.serialization.Serializable

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long
)

class AuthManager(private val storage: SecureStorage) {
    
    suspend fun saveTokens(tokens: AuthTokens) {
        storage.putObject("auth_tokens", tokens, AuthTokens.serializer())
    }
    
    suspend fun getTokens(): AuthTokens? {
        return storage.getObject("auth_tokens", AuthTokens.serializer())
    }
    
    suspend fun clearTokens() {
        storage.removeString("auth_tokens")
    }
    
    suspend fun isAuthenticated(): Boolean {
        val tokens = getTokens() ?: return false
        return System.currentTimeMillis() < tokens.expiresAt
    }
}
```

## Dependency Injection Examples

### Hilt

```kotlin
import android.content.Context
import com.kosikowski.securestore.SecureStorage
import com.kosikowski.securestore.SecureStorageImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage = SecureStorageImpl(context)
}

// Usage in ViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val storage: SecureStorage
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
import com.kosikowski.securestore.SecureStorage
import com.kosikowski.securestore.SecureStorageImpl
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val storageModule = module {
    single<SecureStorage> { SecureStorageImpl(androidContext()) }
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

## Error Handling

```kotlin
import com.kosikowski.securestore.SecureStorage
import java.io.IOException
import timber.log.Timber

class SecureDataManager(private val storage: SecureStorage) {
    
    suspend fun saveSecurely(key: String, value: String): Result<Unit> {
        return try {
            storage.putString(key, value)
            Result.success(Unit)
        } catch (e: IOException) {
            Timber.e(e, "Failed to save $key")
            Result.failure(e)
        }
    }
    
    suspend fun getSecurely(key: String): Result<String?> {
        return try {
            val value = storage.getString(key)
            Result.success(value)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve $key")
            Result.failure(e)
        }
    }
}
```

## Testing

```kotlin
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kosikowski.securestore.SecureStorage
import com.kosikowski.securestore.SecureStorageImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureStorageTest {
    
    private lateinit var storage: SecureStorage
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        storage = SecureStorageImpl(context)
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
    }
}
```


## Full App Example

```kotlin
import android.app.Application
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.kosikowski.securestore.SecureStorage
import com.kosikowski.securestore.SecureStorageImpl
import kotlinx.coroutines.launch

class MyApplication : Application() {
    lateinit var secureStorage: SecureStorage
        private set
    
    override fun onCreate() {
        super.onCreate()
        secureStorage = SecureStorageImpl(this)
    }
}

class LoginActivity : AppCompatActivity() {
    
    private val storage by lazy {
        (application as MyApplication).secureStorage
    }
    
    private fun onLoginSuccess(username: String, token: String) {
        lifecycleScope.launch {
            storage.putString("username", username)
            storage.putString("auth_token", token)
            navigateToHome()
        }
    }
    
    private fun onLogout() {
        lifecycleScope.launch {
            storage.clearAll()
            navigateToLogin()
        }
    }
    
    private fun navigateToHome() {
        // Navigation logic
    }
    
    private fun navigateToLogin() {
        // Navigation logic
    }
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
    
    suspend fun init() {
        _authTokenFlow.value = storage.getString("auth_token")
    }
    
    suspend fun setAuthToken(token: String) {
        storage.putString("auth_token", token)
        _authTokenFlow.value = token
    }
    
    suspend fun clearAuthToken() {
        storage.removeString("auth_token")
        _authTokenFlow.value = null
    }
}
```

## Advanced: Custom Coroutine Dispatcher

```kotlin
import android.content.Context
import com.kosikowski.securestore.SecureStorage
import com.kosikowski.securestore.SecureStorageImpl
import kotlinx.coroutines.Dispatchers

class StorageFactory {
    companion object {
        fun createSecureStorage(context: Context): SecureStorage {
            // Use a custom dispatcher with limited parallelism
            return SecureStorageImpl(
                context = context,
                ioDispatcher = Dispatchers.IO.limitedParallelism(4)
            )
        }
    }
}
```

## Reactive Updates with LiveData

```kotlin
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kosikowski.securestore.SecureStorage
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val storage: SecureStorage
) : ViewModel() {
    
    private val _username = MutableLiveData<String?>()
    val username: LiveData<String?> = _username
    
    init {
        loadUsername()
    }
    
    private fun loadUsername() {
        viewModelScope.launch {
            _username.value = storage.getString("username")
        }
    }
    
    fun saveUsername(name: String) {
        viewModelScope.launch {
            storage.putString("username", name)
            _username.value = name
        }
    }
    
    fun clearUsername() {
        viewModelScope.launch {
            storage.removeString("username")
            _username.value = null
        }
    }
}
```

## Storing Complex Objects

```kotlin
import com.kosikowski.securestore.SecureStorage
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val email: String,
    val preferences: UserPreferences,
    val createdAt: Long
)

@Serializable
data class UserPreferences(
    val theme: String,
    val notifications: Boolean,
    val language: String
)

class ProfileManager(private val storage: SecureStorage) {
    
    suspend fun saveProfile(profile: UserProfile) {
        storage.putObject("user_profile", profile, UserProfile.serializer())
    }
    
    suspend fun getProfile(): UserProfile? {
        return storage.getObject("user_profile", UserProfile.serializer())
    }
    
    suspend fun updatePreferences(preferences: UserPreferences) {
        val currentProfile = getProfile() ?: return
        val updatedProfile = currentProfile.copy(preferences = preferences)
        saveProfile(updatedProfile)
    }
}
```

## Background Work with WorkManager

```kotlin
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kosikowski.securestore.SecureStorageImpl

class DataSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val storage = SecureStorageImpl(context)
    
    override suspend fun doWork(): Result {
        return try {
            // Fetch data from API
            val token = storage.getString("auth_token") ?: return Result.failure()
            
            // Sync data...
            val syncedData = fetchDataFromApi(token)
            
            // Store synced data
            storage.putObject("synced_data", syncedData, SyncedData.serializer())
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
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

## Encrypted File Management

```kotlin
import com.kosikowski.securestore.SecureStorage
import java.io.File

class SecureFileManager(
    private val storage: SecureStorage
) {
    
    suspend fun saveEncryptedFile(file: File): Boolean {
        return try {
            val bytes = file.readBytes()
            storage.saveBlob(file.name, bytes)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    suspend fun readEncryptedFile(fileName: String): ByteArray? {
        return storage.readBlob(fileName)
    }
    
    suspend fun deleteEncryptedFile(fileName: String): Boolean {
        return storage.deleteBlob(fileName)
    }
    
    suspend fun listEncryptedFiles(): List<String> {
        // This is a simplified example
        // In practice, you'd need to maintain a separate index
        return emptyList()
    }
}
```

## Multi-Account Support

```kotlin
import com.kosikowski.securestore.SecureStorage
import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String,
    val username: String,
    val token: String
)

class MultiAccountManager(private val storage: SecureStorage) {
    
    suspend fun addAccount(account: Account) {
        val accounts = getAccounts().toMutableList()
        accounts.add(account)
        storage.putObject("accounts", accounts, AccountList.serializer())
    }
    
    suspend fun getAccounts(): List<Account> {
        return storage.getObject("accounts", AccountList.serializer())?.accounts ?: emptyList()
    }
    
    suspend fun setActiveAccount(accountId: String) {
        storage.putString("active_account_id", accountId)
    }
    
    suspend fun getActiveAccount(): Account? {
        val accountId = storage.getString("active_account_id") ?: return null
        return getAccounts().find { it.id == accountId }
    }
    
    suspend fun removeAccount(accountId: String) {
        val accounts = getAccounts().filter { it.id != accountId }
        storage.putObject("accounts", AccountList(accounts), AccountList.serializer())
    }
}

@Serializable
data class AccountList(val accounts: List<Account>)
```

## Session Management

```kotlin
import com.kosikowski.securestore.SecureStorage
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val userId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val deviceId: String
)

class SessionManager(private val storage: SecureStorage) {
    
    suspend fun createSession(session: Session) {
        storage.putObject("session", session, Session.serializer())
    }
    
    suspend fun getSession(): Session? {
        return storage.getObject("session", Session.serializer())
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
        storage.removeString("session")
    }
}
```

