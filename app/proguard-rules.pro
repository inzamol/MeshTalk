# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\inzam\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools-proguard.html

# Add any project specific keep rules here:

# Tink
-keep class com.google.crypto.tink.** { *; }

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.Json class *
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.Dao
-keep class * extends androidx.room.Entity

# Keep all data models and their members for Moshi/Room/Serialization
-keep @androidx.annotation.Keep class ** { *; }
-keep class in.inzamulhoque.meshtalk.data.local.entity.** { *; }
-keepclassmembers class in.inzamulhoque.meshtalk.data.local.entity.** { *; }

# Keep all Enums and their members strictly
-keepclassmembers enum * { *; }
-keep enum in.inzamulhoque.meshtalk.** { *; }

# Keep Handshake and other protocol DTOs
-keep class in.inzamulhoque.meshtalk.protocol.** { *; }
-keepclassmembers class in.inzamulhoque.meshtalk.protocol.** { *; }

# Moshi Kotlin reflect
-keep class com.squareup.moshi.** { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep interface kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }
