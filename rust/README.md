# RustDroid Rust Workspace

This directory contains the primary Rust workspace for the RustDroid root manager.

## Crates List

1. **`rustdroid-common`**: Shared protocol definitions, constants, custom errors, and serialization objects used by both the daemon and client.
2. **`rustdroid-audit`**: Handles secure file logging under `/data/adb/rustdroid/logs/`.
3. **`rustdroid-boot`**: High-performance parser and verify engine for Android boot and init_boot images.
4. **`rustdroid-policy`**: Evaluates root permission grants for apps based on user records.
5. **`rustdroid-module`**: Core runner for systemless modifications, parsing properties, and execution.
6. **`rustdroid-mount`**: Directs namespace operations and file binding setups.
7. **`rustdroid-daemon`**: The central system service listening on a local domain socket to orchestrate root requests.
8. **`rustdroid-su`**: Standard CLI root entrance invoking process authorization over daemon socket.
9. **`rustdroid-core`**: Orchestration library layer providing external JNI bindings for integration.

## Build Requirements

Building these crates requires the Android NDK to compile for targets like `aarch64-linux-android` or `x86_64-linux-android`.

For quick compilation, run the standard build script at the root:
```bash
./scripts/build-rust.sh
```
