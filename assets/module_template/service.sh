#!/system/bin/sh
# RustDroid Example Module service Script
# This runs after the boot sequence completes in the background.

MODDIR=${0%/*}

# Write test flag
echo "Module late service script executed successfully" > "$MODDIR/service.log"
