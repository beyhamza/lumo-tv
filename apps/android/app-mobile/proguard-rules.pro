# Application-level R8 rules.
#
# Library rules arrive automatically through each module's consumer-rules.pro,
# so this file should stay close to empty. A rule that appears here and not in
# the module that needs it means one of the two applications will shrink
# differently from the other — which is how a bug reproduces on the television
# and nowhere else (ADR 0004).

# Crash reports that point at obfuscated frames are not reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
