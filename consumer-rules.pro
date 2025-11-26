# Consumer ProGuard rules for SecureStore library users

# Keep SecureStorage API
-keep interface com.kosikowski.securestore.SecureStorage { *; }
-keep class com.kosikowski.securestore.SecureStorageImpl {
    public <init>(...);
}

# Tink requires reflection
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

