#!/bin/bash
# RustDroid Packaging Tool (v0.9a Android Boot Integration Packaging Foundation)
# Consolidates target binaries, init configuration, and templates into deployment payload.

set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$DIR/.."

OUT_DIR="$ROOT_DIR/out"
PAYLOAD_DIR="$OUT_DIR/rustdroid_payload"

echo "=== RustDroid: Packaging Android Payload ==="

# Check if target binaries exist
BIN_ARM64_DIR="$ROOT_DIR/rust/target/rustdroid-android/arm64"
if [ ! -f "$BIN_ARM64_DIR/rustdroidd" ] || [ ! -f "$BIN_ARM64_DIR/su" ]; then
    echo "Error: Required Android arm64 binaries (rustdroidd, su) are missing."
    echo "Please compile using: ./scripts/build-rust.sh --target android-arm64 first!"
    exit 1
fi

# Ensure binaries are executable in source dir
chmod 755 "$BIN_ARM64_DIR/rustdroidd"
chmod 755 "$BIN_ARM64_DIR/su"

# 2. Add Android ELF validation
echo "Validating AArch64 ELF executable format..."
for f in "$BIN_ARM64_DIR/rustdroidd" "$BIN_ARM64_DIR/su"; do
    FILE_INFO=$(file "$f")
    if [[ ! "$FILE_INFO" =~ "ELF" ]]; then
        echo "Error: $f is not a valid ELF file!"
        exit 1
    fi
    if [[ ! "$FILE_INFO" =~ "ARM aarch64" && ! "$FILE_INFO" =~ "aarch64" ]]; then
        echo "Error: $f architecture is not AArch64!"
        exit 1
    fi
done

# Recreate payload directory structure
rm -rf "$PAYLOAD_DIR"
mkdir -p "$PAYLOAD_DIR/bin"
mkdir -p "$PAYLOAD_DIR/init"
mkdir -p "$PAYLOAD_DIR/module_template"
mkdir -p "$PAYLOAD_DIR/sepolicy"

# 1. Copy staged binaries
echo "Copying AArch64 Android binaries..."
cp "$BIN_ARM64_DIR/rustdroidd" "$PAYLOAD_DIR/bin/"
cp "$BIN_ARM64_DIR/su" "$PAYLOAD_DIR/bin/"
if [ -f "$BIN_ARM64_DIR/rustdroid-core-cli" ]; then
    cp "$BIN_ARM64_DIR/rustdroid-core-cli" "$PAYLOAD_DIR/bin/"
    chmod 755 "$PAYLOAD_DIR/bin/rustdroid-core-cli"
fi
chmod 755 "$PAYLOAD_DIR/bin/rustdroidd"
chmod 755 "$PAYLOAD_DIR/bin/su"

# 2. Copy init configuration
echo "Copying init.rustdroid.rc..."
if [ -f "$ROOT_DIR/assets/init.rustdroid.rc" ]; then
    cp "$ROOT_DIR/assets/init.rustdroid.rc" "$PAYLOAD_DIR/init/"
    chmod 644 "$PAYLOAD_DIR/init/init.rustdroid.rc"
else
    echo "Warning: assets/init.rustdroid.rc not found."
fi

# 3. Copy module template and sepolicy
echo "Copying module templates and sepolicy assets..."
if [ -d "$ROOT_DIR/assets/module_template" ]; then
    cp -r "$ROOT_DIR/assets/module_template/"* "$PAYLOAD_DIR/module_template/" || true
fi
if [ -d "$ROOT_DIR/assets/sepolicy" ]; then
    cp -r "$ROOT_DIR/assets/sepolicy/"* "$PAYLOAD_DIR/sepolicy/" || true
fi

# 4. Generate package metadata.json with SHA-256 hashes and file modes
echo "Generating metadata.json..."
BUILD_TIME=$(date -u +"%Y-%m-%dT%H:%M:%SZ")

HASH_DAEMON=$(sha256sum "$PAYLOAD_DIR/bin/rustdroidd" | awk '{print $1}')
SIZE_DAEMON=$(stat -c%s "$PAYLOAD_DIR/bin/rustdroidd")

HASH_SU=$(sha256sum "$PAYLOAD_DIR/bin/su" | awk '{print $1}')
SIZE_SU=$(stat -c%s "$PAYLOAD_DIR/bin/su")

HASH_RC=$(sha256sum "$PAYLOAD_DIR/init/init.rustdroid.rc" | awk '{print $1}')
SIZE_RC=$(stat -c%s "$PAYLOAD_DIR/init/init.rustdroid.rc")

cat <<EOF > "$PAYLOAD_DIR/metadata.json"
{
  "rustdroid_version": "v0.9a",
  "payload_version": 2,
  "target_arch": "aarch64",
  "build_timestamp": "$BUILD_TIME",
  "binaries": [
    "rustdroidd",
    "su"
  ],
  "safety_scope": {
    "execution_default_enabled": false,
    "module_mounting_enabled": false,
    "hiding_enabled": false,
    "bypass_enabled": false
  },
  "manifest": [
    {
      "path": "bin/rustdroidd",
      "sha256": "$HASH_DAEMON",
      "size": $SIZE_DAEMON,
      "mode": "0755"
    },
    {
      "path": "bin/su",
      "sha256": "$HASH_SU",
      "size": $SIZE_SU,
      "mode": "0755"
    },
    {
      "path": "init/init.rustdroid.rc",
      "sha256": "$HASH_RC",
      "size": $SIZE_RC,
      "mode": "0644"
    }
  ],
  "runtime_modes": {
    "config.json": "0644",
    "policy.json": "0600",
    "logs/": "0700",
    "modules/": "0700",
    "run/": "0700"
  }
}
EOF

# 5. Zip payload for release distribution
cd "$OUT_DIR"
zip -q -r "rustdroid_release.zip" "rustdroid_payload"

echo "=== Release package successfully generated! ==="
echo "Payload: out/rustdroid_payload/"
echo "Archive: out/rustdroid_release.zip"
echo ""
echo "RustDroid v0.9a package generated."
echo "This package prepares boot integration assets only."
echo "It does not flash devices."
echo "It does not bypass Android security."
echo "It does not hide root."
echo "Execution remains disabled by default unless explicitly enabled by config and policy."
