# שמירה על מאפיינים של קוד מקור לדיבוג
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# כללים כלליים
-keepattributes *Annotation*,Signature,InnerClasses

# הגנה על Kotlin
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# שמירה על מחלקות מרכזיות
-keep class com.example.adskipper2.** { *; }

# Android core
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.accessibilityservice.AccessibilityService

# Androidx
-keep class androidx.** { *; }
-dontwarn androidx.**

# MLKit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Serialization
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# הסרת לוגים
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# שמירה על BuildConfig
-keep class **.BuildConfig { *; }