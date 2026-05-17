
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keepattributes SourceFile, LineNumberTable
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**


-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

-keep class com.app.lutiwallet.LutiMessagingService { *; }


-keep class com.app.lutiwallet.UpdateManager { *; }
-keep class com.app.lutiwallet.pantallas.Mensaje { *; }

-keep class androidx.core.content.FileProvider { *; }


-keep class javax.crypto.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class android.security.** { *; }
-dontwarn android.security.**
-keep class org.bitcoinj.** { *; }
-dontwarn org.bitcoinj.**


-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-keepnames class com.fasterxml.jackson.** { *; }

-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }


-keep class com.app.lutiwallet.** { *; }