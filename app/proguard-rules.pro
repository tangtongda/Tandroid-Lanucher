# R8 rules for the release build.
#
# Nothing here needs reflection-based keeps: JSON is parsed by hand with org.json, components
# (activity, accessibility service, FileProvider) are kept via the manifest, and Compose / Coil /
# coroutines ship their own consumer rules.

# Readable stack traces in crash logs (class names still obfuscated; map with mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
