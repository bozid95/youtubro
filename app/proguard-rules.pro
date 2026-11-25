# Adapted from Mochy Music's rules.

# BotGuard WebView bridge
-keepclassmembers class pro.iyou.data.stream.potoken.PoTokenWebView {
    @android.webkit.JavascriptInterface public *;
}
-keep class pro.iyou.data.stream.potoken.** { *; }

# kotlinx.serialization (innertube models)
-if @kotlinx.serialization.Serializable class **
-keepclasseswithmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclasseswithmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclasseswithmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,*Annotation*,Signature,InnerClasses

# NewPipeExtractor + Rhino
-keep class org.schabi.newpipe.extractor.services.youtube.protos.** { *; }
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.javascript.engine.** { *; }
-dontwarn org.mozilla.javascript.JavaToJSONConverters
-dontwarn org.mozilla.javascript.tools.**
-keep class javax.script.** { *; }
-dontwarn javax.script.**
-keep class jdk.dynalink.** { *; }
-dontwarn jdk.dynalink.**
-dontwarn java.beans.**
-dontwarn com.google.re2j.**

# InnerTubeX
-keep class com.metrolist.innertubex.** { *; }
-dontwarn com.metrolist.innertubex.**

# Ktor / OkHttp
-keep class io.ktor.client.engine.okhttp.** { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn javax.servlet.ServletContainerInitializer

-keepclassmembers class * {
    void resume(...);
    void resumeWithException(...);
}
