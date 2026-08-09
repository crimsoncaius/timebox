# kotlinx.serialization keeps its generated serializers on the classes themselves.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.timebox.android.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.timebox.android.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit interfaces are reflective.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
