# Keep the prebuilt payloads and profile files intact.
-keepattributes *Annotation*
-dontwarn com.typesafe.config.**
-keep class app.awesom.root.** { *; }
