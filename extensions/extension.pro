# Shared R8 rules across all extensions under :extensions.
# Patterned on MorpheApp/morphe-patches/extensions/proguard-rules.pro.
#
# The shim classes are invoked by bytecode added at patch-application time (the
# patch rewrites Settings.{Global,Secure,System} invoke-static refs to point at
# them). R8 sees no references at extension-build time, so without an explicit
# keep rule it would strip the entire shim — and the patched app would NoClassDefFoundError.

-dontobfuscate
-dontoptimize
-keepattributes *

-keep class io.github.bholeykabhakt.extension.** {
  *;
}

# Shim AssetPackLocation lives in the Play Core package (to reach the
# package-private super ctor) and is only referenced by patch-added bytecode.
-keep class com.google.android.play.core.assetpacks.ShimAssetPackLocation {
  *;
}

# R8 has been observed stripping Kotlin intrinsics used by some extension code.
# Unclear why, but keeping them is cheap insurance — matches upstream practice.
-keep class kotlin.jvm.internal.Intrinsics {
    public static *;
}
