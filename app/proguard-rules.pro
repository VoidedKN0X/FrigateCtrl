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

# No logging at all in release builds: FrigateCtrl's own code doesn't log, and any Log call that
# sneaks in later (or one in a library) is stripped.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}

# WorkManager keeps its job list in a Room 2.6.1 database, and Room creates the generated
# WorkDatabase_Impl by reflection through its no-argument constructor. Room 2.6.1's own rule
# (-keep class * extends androidx.room.RoomDatabase) names no members, and the R8 in Android
# Gradle Plugin 9 no longer keeps a constructor for such a rule, so without this line the app
# would crash at start (NoSuchMethodException: WorkDatabase_Impl.<init>). Same rule Room 2.7 ships.
-keep class * extends androidx.room.RoomDatabase { void <init>(); }
