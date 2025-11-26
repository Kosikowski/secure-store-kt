# Keep SecureStorage interface and implementation
}
    *;
-keepclassmembers @kotlinx.serialization.Serializable class ** {
# Keep data classes used with serialization

}
    kotlinx.serialization.KSerializer serializer(...);
-keepclasseswithmembers class com.kosikowski.securestore.** {
}
    *** Companion;
-keepclassmembers class com.kosikowski.securestore.** {
-keep,includedescriptorclasses class com.kosikowski.securestore.**$$serializer { *; }

}
    kotlinx.serialization.KSerializer serializer(...);
-keepclasseswithmembers class kotlinx.serialization.json.** {
}
    *** Companion;
-keepclassmembers class kotlinx.serialization.json.** {

-dontnote kotlinx.serialization.AnnotationsKt
-keepattributes *Annotation*, InnerClasses
# Keep kotlinx.serialization

-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }
# Keep Tink classes

-keep class com.kosikowski.securestore.SecureStorageImpl { *; }
-keep interface com.kosikowski.securestore.SecureStorage { *; }

