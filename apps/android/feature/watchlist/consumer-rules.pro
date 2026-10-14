# Consumer R8 rules for this module.
#
# Rules here travel with the module into whichever application consumes it, so
# both applicationIds shrink identically (ADR 0004). A keep rule that only one
# app needs belongs in that app's proguard-rules.pro, not here.
#
# Empty on purpose: nothing in this module relies on reflection.
