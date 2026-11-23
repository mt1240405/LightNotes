# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# JLatexMath & Markwon rules
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn ru.noties.jlatexmath.**
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

# Prism4j
-keep class io.noties.prism4j.** { *; }
-dontwarn io.noties.prism4j.**

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
