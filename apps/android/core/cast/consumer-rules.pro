# Consumer R8 rules for core:cast (phone application only).
#
# The Cast SDK instantiates the options provider by reflection, from the class
# name declared in this module's manifest (OPTIONS_PROVIDER_CLASS_NAME).
-keep class tv.lumo.android.core.cast.CastOptionsProvider { <init>(); }
