# Testing Guide for SecureStore

## Test Configuration

SecureStore uses **instrumented tests** (also called Android Tests) rather than unit tests because:
- The library requires Android framework APIs (Context, SharedPreferences, File system)
- Encryption uses Android Keystore which requires a real Android environment
- Tests verify actual encryption/decryption on Android

## Running Tests

### Prerequisites
You need either:
- A physical Android device connected via USB with Developer Mode enabled
- An Android emulator running (API 24+)

### Running Instrumented Tests

```bash
# Start an emulator first, then run:
./gradlew connectedAndroidTest
```

### Common Issues

#### "Test events were not received"
This error occurs when:
- No device/emulator is connected
- The emulator is not fully booted
- USB debugging is not enabled on the physical device

**Solution:**
1. Start an Android emulator (API 24+)
2. Wait for it to fully boot
3. Then run: `./gradlew connectedAndroidTest`

#### "No connected devices!"
**Solution:**
```bash
# Check connected devices
adb devices

# If empty, start an emulator or connect a device
```

### Creating an Emulator (if needed)

Using Android Studio:
1. Tools → Device Manager
2. Create Device → Select a device (e.g., Pixel 5)
3. Select System Image (API 29 or higher recommended)
4. Finish and start the emulator

Or via command line:
```bash
# List available system images
sdkmanager --list | grep system-images

# Create emulator
avdmanager create avd -n test_device -k "system-images;android-29;google_apis;x86_64"

# Start emulator
emulator -avd test_device
```

## Test Structure

All tests are located in:
```
src/androidTest/kotlin/com/kosikowski/securestore/
└── SecureStorageInstrumentedTest.kt
```

### Test Categories

1. **Basic Operations** (11 tests)
   - String storage/retrieval
   - Object serialization
   - Blob (file) operations
   - Clear all data

2. **Thread-Safety** (9 tests)
   - Concurrent file writes
   - Concurrent reads/writes
   - Stress tests with mixed operations

3. **Edge Cases** (3 tests)
   - Empty data
   - Large payloads (1MB)

**Total: 23 comprehensive test cases**

## Running Specific Tests

```bash
# Run all tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew connectedAndroidTest --tests "*.SecureStorageInstrumentedTest"

# Run specific test method
./gradlew connectedAndroidTest --tests "*.test_put_and_get_string_returns_same_value"
```

## Test Reports

After running tests, view the HTML report:
```
build/reports/androidTests/connected/index.html
```

## Troubleshooting
## Troubleshooting

### Tests fail with SecurityException
- Some devices/emulators have issues with Keystore
- Try a different API level (29 or 30 work well)
- Ensure device is not in restricted mode

### Tests timeout
- Increase timeout in test configuration
- Close other apps on emulator to free resources
- Use x86_64 emulator for better performance

### Import errors in IDE
- The IDE may show "unresolved reference" errors for Android APIs
- This is normal - the code compiles successfully with Gradle
- Run `./gradlew compileReleaseSources` to verify compilation

## Test Execution Time

Approximate execution times:
- All tests: ~2-3 minutes on emulator
- Basic operations: ~30 seconds
- Thread-safety tests: ~1-2 minutes
- On physical device: Usually faster than emulator

## Adding New Tests

When adding tests:
1. Place in `src/androidTest/kotlin/com/kosikowski/securestore/`
2. Annotate class with `@RunWith(AndroidJUnit4::class)`
3. Use `runBlocking` for suspend functions
4. Clear storage in `@Before` and `@After`

Example:
```kotlin
@Test
fun test_my_new_feature_works_correctly() = runBlocking {
    // Given
    val testData = "test"
    
    // When
    secureStorage.putString("key", testData)
    
    // Then
    assertEquals(testData, secureStorage.getString("key"))
}
```