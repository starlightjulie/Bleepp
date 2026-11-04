# kotlinx.serialization keeps its own generated serializers; these keep the @Serializable models intact.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class com.bleepp.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.bleepp.** {
    kotlinx.serialization.KSerializer serializer(...);
}
