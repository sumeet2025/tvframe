# Retrofit & Kotlinx Serialization
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class com.nostr.tvframe.** { *; }

# jcifs-ng SMBv2/v3 BouncyCastle & CIFS rules
-keep class jcifs.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# jcifs-ng optional integrations not present on Android (Kerberos/JAAS/GSS, servlet NTLM filter)
-dontwarn javax.security.auth.**
-dontwarn org.ietf.jgss.**
-dontwarn javax.servlet.**
# slf4j (used by jcifs-ng) without a binding: falls back to no-op logger
-dontwarn org.slf4j.**
# Tink (via androidx.security-crypto) compile-only annotations
-dontwarn com.google.errorprone.annotations.**

# Retrofit service interfaces (suspend functions use generic signatures via reflection)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.nostr.tvframe.data.remote.** { *; }
