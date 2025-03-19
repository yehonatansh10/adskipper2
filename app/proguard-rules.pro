# שמירה על מידע של שורות קוד לדיבוג
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# כללים עבור Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }

# כללים עבור MLKit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# שמירה על מודלים של MLKit
-keep class com.google.mlkit.vision.text.** { *; }
-keep class com.google.mlkit.vision.label.** { *; }

# כללים כלליים לאנדרואיד
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

# הסרת מידע ניפוי שגיאות
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# הגנה על מידע רגיש (מפתחות הצפנה)
-keep class com.example.adskipper2.storage.** { *; }
-keepclassmembers class com.example.adskipper2.storage.** {
    private static final java.lang.String *;
}

# הגנה על Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# כללים עבור Accessibility Service
-keep class * extends android.accessibilityservice.AccessibilityService
-keepclassmembers class * extends android.accessibilityservice.AccessibilityService {
    public <methods>;
}

# שמירה על BuildConfig
-keep class **.BuildConfig { *; }

# שמירה על מודלים וקלאסים חשובים
-keep class com.example.adskipper2.** { *; }

# שמירה על קוד של ספריות חיצוניות חשובות
-keep class androidx.compose.** { *; }
-keep class com.google.mlkit.** { *; }

# שמירה על מידע שורות קוד לדיבוג
-keepattributes SourceFile,LineNumberTable

# הגנה על קבצי נכסים חשובים
-keep assets/default_keywords.json