# Gson maps JSON by field name: keep API models un-renamed for release (R8) builds
-keep class com.familyguard.app.data.model.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
