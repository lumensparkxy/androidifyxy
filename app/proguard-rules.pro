# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================
# Firebase Firestore - Keep data classes
# ============================================

# Keep all data classes in the data package (used by Firestore)
-keep class com.maswadkar.developers.androidify.data.** { *; }

# Keep Firebase Firestore annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep classes that use Firebase annotations
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.DocumentId <fields>;
    @com.google.firebase.firestore.ServerTimestamp <fields>;
}

# Keep Firestore model classes with default constructors
-keepclassmembers class * {
    public <init>();
}

# ============================================
# Firebase Auth & Credential Manager
# ============================================

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }

# Google Identity / Credential Manager
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# ============================================
# General Firebase rules
# ============================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ============================================
# Kotlin
# ============================================

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }
-keepattributes RuntimeVisibleAnnotations

# Keep Kotlin data classes
-keepclassmembers class * {
    public <init>(...);
}
