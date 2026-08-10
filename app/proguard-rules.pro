# Bouncy Castle registers JCA algorithms through provider configuration and
# reflection; R8 cannot safely infer those implementations.
-keep class org.bouncycastle.** { *; }
-dontwarn javax.naming.**
