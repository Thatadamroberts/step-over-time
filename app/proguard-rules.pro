# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.stepquest.core.**$$serializer { *; }
-keepclassmembers class com.stepquest.core.** { *** Companion; }
-keepclasseswithmembers class com.stepquest.core.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.stepquest.app.steps.** { *; }
