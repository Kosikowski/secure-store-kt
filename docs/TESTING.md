# Testing Guide for SecureStore

## Test Configuration

SecureStore has two kinds of tests:

- **Unit tests** (`src/test`) run on the JVM and cover logic without Android dependencies, such as
  deciding whether a failure means the keysets are lost.
- **Instrumented tests** (`src/androidTest`) run on a device or emulator. Most behaviour needs them,
  because the library relies on Android Keystore, SharedPreferences and the app's storage.

## Running Tests

### Unit Tests

```bash
./gradlew test
```

### Instrumented Tests

You need either:
- A physical Android device connected via USB with Developer Mode enabled
- An Android emulator running (API 24+)

```bash
./gradlew connectedAndroidTest
```

CI runs both on every pull request, the instrumented tests on an API 30 emulator
(`.github/workflows/ci.yml`).

### Running Specific Tests

```bash
# One instrumented test class
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kosikowski.securestore.KeysetLossInstrumentedTest

# One instrumented test method
./gradlew connectedAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.kosikowski.securestore.SecureStorageInstrumentedTest#putAndGetString_roundTrip

# One unit test class (the aggregate `test` task does not accept --tests)
./gradlew testDebugUnitTest --tests "com.kosikowski.securestore.KeysetLossTest"
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
avdmanager create avd -n test_device -k "system-images;android-30;google_apis;x86_64"

# Start emulator
emulator -avd test_device
```

## Test Structure

```
src/test/kotlin/com/kosikowski/securestore/
└── KeysetLossTest.kt                     # 8 tests: which failures mean the keysets are lost

src/androidTest/kotlin/com/kosikowski/securestore/
├── SecureStorageInstrumentedTest.kt      # 38 tests: strings, objects, blobs, thread safety, edge cases, configuration
├── KeysetLossInstrumentedTest.kt         # 9 tests: lost master key or corrupt keyset, RESET/THROW, reset(), onKeysetReset
├── KeysetStorageInstrumentedTest.kt      # 5 tests: where keysets are stored, copying keysets written by 1.0.0
├── StoreInstancesInstrumentedTest.kt     # 6 tests: several instances of one store, resets during writes, blob locks, master key aliases, presets
├── NameEncryptionInstrumentedTest.kt     # 4 tests: encrypted key and file names, removal of 1.0.0 entries
├── BlobFilesInstrumentedTest.kt          # 3 tests: atomic blob writes
├── KeyProtectionInstrumentedTest.kt      # 2 tests: HARDWARE_REQUIRED and isHardwareBacked
└── KeyRotationInstrumentedTest.kt        # 1 test: rotateKeys()
```

**Total: 8 unit tests and 68 instrumented tests.**

The key protection tests check where Android Keystore actually keeps the master key, so they pass both
on devices with secure hardware and on emulators that keep keys in software.

## Test Reports

After running tests, view the HTML reports:
```
build/reports/tests/testDebugUnitTest/index.html
build/reports/androidTests/connected/debug/index.html
```

## Troubleshooting

### Tests fail with SecurityException
- Some devices/emulators have issues with Keystore
- Try a different API level (29 or 30 work well)
- Ensure device is not in restricted mode

### Tests timeout
- Close other apps on the emulator to free resources
- Use an x86_64 emulator for better performance

### Import errors in IDE
- The IDE may show "unresolved reference" errors for Android APIs
- This is normal - the code compiles successfully with Gradle
- Run `./gradlew compileReleaseSources` to verify compilation

## Test Execution Time

The 68 instrumented tests take about a minute on a physical device (an Android 12 terminal); emulators
are slower. The unit tests take a few seconds.

## Adding New Tests

When adding instrumented tests:
1. Place them in `src/androidTest/kotlin/com/kosikowski/securestore/`
2. Annotate the class with `@RunWith(AndroidJUnit4::class)`
3. Use `runBlocking` for suspend functions
4. Use a namespace unique to the test class instance, so tests don't share keysets or data
5. In `@After`, call `reset()` and delete the namespace's master key from Android Keystore

Example:
```kotlin
@RunWith(AndroidJUnit4::class)
class MyFeatureInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val namespace = "my_feature_${System.nanoTime()}"
    private val storage = SecureStorageImpl(context, SecureStoreConfig.Builder().namespace(namespace).build())

    @After
    fun tearDown() =
        runBlocking {
            storage.reset()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("secure_store_master_key_$namespace")
        }

    @Test
    fun myFeature_worksCorrectly() =
        runBlocking {
            storage.putString("key", "value")

            assertEquals("value", storage.getString("key"))
        }
}
```
