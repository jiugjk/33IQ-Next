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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ---------------------------------------------------------------------------
# 33IQ Next keep rules
#
# Most libraries used here (OkHttp, Jsoup, Room, Koin, AndroidX Navigation,
# kotlinx.serialization) ship their own consumer rules, so only this app's own
# reflectively-reached entry points are listed below.
# ---------------------------------------------------------------------------

# Navigation routes are @Serializable and are looked up by generated serializers,
# not from call sites, so their names and members must survive.
-keep,includedescriptorclasses class com.jiugjk.iq33.app.presentation.NavigationRoute { *; }
-keep,includedescriptorclasses class com.jiugjk.iq33.app.presentation.NavigationRoute$* { *; }
-keepclassmembers class com.jiugjk.iq33.app.presentation.NavigationRoute$* {
    kotlinx.serialization.KSerializer serializer(...);
}

# Instantiated by the framework from the manifest.
-keep class com.jiugjk.iq33.app.IqApplication
-keep class com.jiugjk.iq33.app.presentation.MainActivity

# Timber's release tree is stripped along with the debug-only logging, but keep
# the line metadata so a crash report from a release build is still readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
