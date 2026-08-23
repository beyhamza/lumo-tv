# Consumer R8 rules for core:network.
#
# These travel into both applications, so `tv.lumo.android` and
# `tv.lumo.androidtv` shrink identically (ADR 0004). Every rule below exists
# because something here is resolved by reflection at runtime, which R8 cannot
# see — and the failure mode is a release build that parses no JSON at all while
# the debug build works perfectly.

# ---- Generated contract models ----------------------------------------------
# Moshi builds adapters for these by reflection (the generator emits @Json but
# not @JsonClass(generateAdapter = true)), so the class names, the constructors
# and the property names all have to survive.
-keep class tv.lumo.android.network.generated.model.** { *; }
-keepclassmembers class tv.lumo.android.network.generated.model.** {
    <init>(...);
    *;
}

# Keep the Kotlin metadata Moshi's reflective adapter reads to find default
# values and nullability. Without it, an absent optional field throws instead of
# taking its default.
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @com.squareup.moshi.Json <fields>;
}

# ---- Retrofit ----------------------------------------------------------------
# Retrofit builds implementations of the generated API interfaces at runtime and
# reads their annotations and generic signatures.
-keep,allowobfuscation interface tv.lumo.android.network.generated.api.*
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Retrofit's own reflection over suspend functions and Response<T>.
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---- OkHttp ------------------------------------------------------------------
# OkHttp references optional platform classes that are absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
