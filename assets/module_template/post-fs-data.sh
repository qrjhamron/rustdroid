#!/system/bin/sh
# RustDroid Example Module post-fs-data Script
# This runs before main Android frameworks mount. Useful for early bind-mounting.

MODDIR=${0%/*}

# Write test flag
echo "Module post-fs-data script executed successfully" > "$MODDIR/post-fs-data.log"
