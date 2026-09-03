# Shared R8 rules for the application modules, applied by AndroidApplicationConventionPlugin.

# The app is open source, so obfuscation protects nothing and would only make crash reports and
# the logs shown in the app unreadable (no retrace step exists in that path). R8 still shrinks
# and optimizes.
-dontobfuscate
