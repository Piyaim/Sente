# Règles R8 propres à Sente.

# valhalla-mobile : le code natif appelle ses classes par leur nom, et ses modèles JSON sont lus
# par réflexion (Moshi). On garde tout le paquet, plutôt que de risquer un plantage au calcul.
-keep class com.valhalla.** { *; }
-keep class com.osrm.** { *; }
-keep class kotlin.Metadata { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Annotations de compilation que certaines bibliothèques mentionnent sans les fournir : sans effet à l'exécution.
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-dontwarn com.google.errorprone.annotations.**
