# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.airouter.data.model.** { *; }
-keep class com.airouter.data.remote.dto.** { *; }
-dontwarn kotlinx.serialization.**
