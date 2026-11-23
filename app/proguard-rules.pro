# Project specific ProGuard rules for LightNotes Lite
-keepattributes *Annotation*
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# Markwon core & extension rules
-keep class io.noties.markwon.** { *; }
-dontwarn io.noties.markwon.**

# Kotlinx Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Coroutines
-dontwarn kotlinx.coroutines.**
