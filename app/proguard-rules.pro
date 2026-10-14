# Project-specific R8 rules. Phase 1 needs none beyond the default optimize config.
# Room, serialization, and AI provider adapters will add rules in later phases if required.

# PdfBox-Android: optional JPEG2000 decoder is not bundled.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
