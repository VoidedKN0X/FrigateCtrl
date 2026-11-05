# R8 rules for FrigateCtrl release builds.
# Libraries in use (OkHttp, Media3, AndroidX, Compose) ship their own consumer rules.
# JSON is parsed by hand with org.json (part of Android), so nothing is (de)serialised by reflection;
# the rules below are deliberately conservative.

# Two-way talk: the hidden WebView page calls these methods by name through addJavascriptInterface.
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.vknxworks.frigatectrl.ui.TalkSession$Bridge { *; }

# App data/model classes (built from JSON by hand; kept intact in case anything reflects on them).
-keep class com.vknxworks.frigatectrl.data.** { *; }

# Keep enum helpers used via entries/valueOf (TalkState and others).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** getEntries();
}

# Readable crash stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# OkHttp's optional TLS providers (not used on Android).
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# No debug, verbose or info logging in release builds (FrigateCtrl's own code doesn't log; this also
# silences libraries).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
