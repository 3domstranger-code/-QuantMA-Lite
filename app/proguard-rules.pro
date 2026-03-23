# JNI — must keep native method signatures
-keep class com.quantma.lite.data.inference.LlamaJni { native <methods>; }
-keepclasseswithmembernames class * { native <methods>; }

# Hilt DI
-keep,allowobfuscation @dagger.hilt.android.HiltAndroidApp class *
-keep,allowobfuscation @dagger.hilt.android.lifecycle.HiltViewModel class *
-keep @dagger.Module class *
-keep @dagger.hilt.InstallIn class *

# Room
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# JGit
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**

# Sora Editor + TextMate
-keep class io.github.rosemoe.sora.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-keep class org.joni.** { *; }
-dontwarn io.github.rosemoe.sora.**

# Compose
-dontwarn androidx.compose.**

# Kotlin
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }

# Strip debug/verbose logs in release
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
}

# Honeypot classes must survive R8 (Phase 7)
-keep class com.quantma.lite.core.** { *; }
-keep class com.quantma.lite.sync.** { *; }
-keep class com.quantma.lite.ml.** { *; }
