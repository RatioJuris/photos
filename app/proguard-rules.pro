# SPDX-License-Identifier: GPL-3.0-or-later
# BouncyCastle: only the Argon2 generator is used directly (no JCA provider registration).
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters$Builder { *; }

# Strip verbose/debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
