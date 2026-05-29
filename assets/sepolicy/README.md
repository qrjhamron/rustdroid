# SELinux Policy Customizations

This folder contains SELinux policies (`.te`, `.cil`) required to define the `u:r:su:s0` transition rules, granting the RustDroid daemon permission to handle elevated socket queries, process namespaces, and bind mounts safely.
