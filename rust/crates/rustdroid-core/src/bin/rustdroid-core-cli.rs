use std::path::Path;
use rustdroid_boot::{run_audit, patch_boot_image_v0_7, PayloadInjectionPlan, InjectionEntry};

fn main() {
    let args: Vec<String> = std::env::args().collect();
    
    // 1. Intercept boot image orchestration actions (CLI offline/patch flow)
    if args.len() > 1 {
        let action = args[1].as_str();
        match action {
            "audit" => {
                if args.len() < 3 {
                    eprintln!("Usage: rustdroid-core-cli audit <image_path>");
                    std::process::exit(1);
                }
                let image_path = Path::new(&args[2]);
                match std::fs::read(image_path) {
                    Ok(bytes) => {
                        match run_audit(&bytes) {
                            Ok(report) => {
                                println!("{}", serde_json::to_string_pretty(&report).unwrap());
                                std::process::exit(0);
                            }
                            Err(e) => {
                                eprintln!("Error auditing image: {:?}", e);
                                std::process::exit(1);
                            }
                        }
                    }
                    Err(e) => {
                        eprintln!("Error reading image: {}", e);
                        std::process::exit(1);
                    }
                }
            }
            "plan" => {
                if args.len() < 5 {
                    eprintln!("Usage: rustdroid-core-cli plan <image_path> --payload <payload_dir>");
                    std::process::exit(1);
                }
                let mut payload_dir = "";
                let mut i = 3;
                while i < args.len() {
                    if args[i] == "--payload" && i + 1 < args.len() {
                        payload_dir = &args[i + 1];
                        break;
                    }
                    i += 1;
                }
                if payload_dir.is_empty() {
                    eprintln!("Error: --payload is required for plan action");
                    std::process::exit(1);
                }

                // Construct and display the PayloadInjectionPlan
                let mut plan = PayloadInjectionPlan::new("aarch64");
                let p_dir = Path::new(payload_dir);
                plan.add_entry(InjectionEntry {
                    source_path: p_dir.join("bin/rustdroidd"),
                    target_path: "/data/adb/rustdroid/bin/rustdroidd".to_string(),
                    is_ramdisk: false,
                    file_size: std::fs::metadata(p_dir.join("bin/rustdroidd")).map(|m| m.len() as usize).unwrap_or(0),
                    permissions: "0755".to_string(),
                });
                plan.add_entry(InjectionEntry {
                    source_path: p_dir.join("bin/su"),
                    target_path: "/data/adb/rustdroid/bin/su".to_string(),
                    is_ramdisk: false,
                    file_size: std::fs::metadata(p_dir.join("bin/su")).map(|m| m.len() as usize).unwrap_or(0),
                    permissions: "0755".to_string(),
                });
                plan.add_entry(InjectionEntry {
                    source_path: p_dir.join("init/init.rustdroid.rc"),
                    target_path: "init.rustdroid.rc".to_string(),
                    is_ramdisk: true,
                    file_size: std::fs::metadata(p_dir.join("init/init.rustdroid.rc")).map(|m| m.len() as usize).unwrap_or(0),
                    permissions: "0644".to_string(),
                });

                if let Err(e) = plan.validate_safety() {
                    eprintln!("Safety validation error: {:?}", e);
                    std::process::exit(1);
                }

                println!("{}", serde_json::to_string_pretty(&plan).unwrap());
                std::process::exit(0);
            }
            "patch" => {
                if args.len() < 7 {
                    eprintln!("Usage: rustdroid-core-cli patch <image_path> --payload <payload_dir> --output <output_path> [--force]");
                    std::process::exit(1);
                }
                let image_path = &args[2];
                let mut payload_dir = "";
                let mut output_path = "";
                let mut force = false;
                let mut i = 3;
                while i < args.len() {
                    match args[i].as_str() {
                        "--payload" => {
                            if i + 1 < args.len() {
                                payload_dir = &args[i + 1];
                                i += 1;
                            }
                        }
                        "--output" => {
                            if i + 1 < args.len() {
                                output_path = &args[i + 1];
                                i += 1;
                            }
                        }
                        "--force" => {
                            force = true;
                        }
                        _ => {}
                    }
                    i += 1;
                }

                if payload_dir.is_empty() || output_path.is_empty() {
                    eprintln!("Error: --payload and --output are required for patch action");
                    std::process::exit(1);
                }

                match patch_boot_image_v0_7(
                    Path::new(image_path),
                    Path::new(output_path),
                    Path::new(payload_dir),
                    force,
                ) {
                    Ok(report) => {
                        println!("{}", serde_json::to_string_pretty(&report).unwrap());
                        println!("\nRustDroid v0.7 created a patched image file only.");
                        println!("It did not flash any device.");
                        println!("It did not bypass Android security.");
                        println!("It did not hide root.");
                        println!("Real-device boot testing is a separate manual validation step.");
                        std::process::exit(0);
                    }
                    Err(e) => {
                        eprintln!("Error patching boot image: {:?}", e);
                        std::process::exit(1);
                    }
                }
            }
            "verify" => {
                if args.len() < 3 {
                    eprintln!("Usage: rustdroid-core-cli verify <image_path>");
                    std::process::exit(1);
                }
                let image_path = Path::new(&args[2]);
                match rustdroid_boot::verify_patched_boot_image(image_path) {
                    Ok(report) => {
                        println!("{}", serde_json::to_string_pretty(&report).unwrap());
                        std::process::exit(0);
                    }
                    Err(e) => {
                        eprintln!("Error verifying patched image: {:?}", e);
                        std::process::exit(1);
                    }
                }
            }
            "validate-module" => {
                if args.len() < 3 {
                    eprintln!("Usage: rustdroid-core-cli validate-module <zip_path>");
                    std::process::exit(1);
                }
                let zip_path = &args[2];
                let payload = serde_json::json!({
                    "zip_path": zip_path
                }).to_string();
                println!("{}", rustdroid_core::validate_module_zip(&payload));
                std::process::exit(0);
            }
            "install-module" => {
                if args.len() < 3 {
                    eprintln!("Usage: rustdroid-core-cli install-module <zip_path> [--modules-dir <dir>]");
                    std::process::exit(1);
                }
                let zip_path = &args[2];
                let mut m_dir = None;
                let mut idx = 3;
                while idx < args.len() {
                    if args[idx] == "--modules-dir" && idx + 1 < args.len() {
                        m_dir = Some(args[idx + 1].clone());
                        break;
                    }
                    idx += 1;
                }
                if let Some(ref dir) = m_dir {
                    if let Some(parent) = Path::new(dir).parent() {
                        std::env::set_var("RUSTDROID_DATA_DIR", parent.to_string_lossy().to_string());
                    }
                }
                let payload = serde_json::json!({
                    "zip_path": zip_path,
                    "modules_dir": m_dir
                }).to_string();
                println!("{}", rustdroid_core::install_module(&payload));
                std::process::exit(0);
            }
            "list-modules" => {
                let mut m_dir = None;
                let mut idx = 2;
                while idx < args.len() {
                    if args[idx] == "--modules-dir" && idx + 1 < args.len() {
                        m_dir = Some(args[idx + 1].clone());
                        break;
                    }
                    idx += 1;
                }
                if let Some(ref dir) = m_dir {
                    if let Some(parent) = Path::new(dir).parent() {
                        std::env::set_var("RUSTDROID_DATA_DIR", parent.to_string_lossy().to_string());
                    }
                }
                println!("{}", rustdroid_core::list_modules());
                std::process::exit(0);
            }
            "enable-module" => {
                if args.len() < 3 {
                    eprintln!("Usage: rustdroid-core-cli enable-module <module_id> [--modules-dir <dir>] [--force]");
                    std::process::exit(1);
                }
                let module_id = &args[2];
                let mut m_dir = None;
                let mut force = false;
                let mut idx = 3;
                while idx < args.len() {
                    if args[idx] == "--modules-dir" && idx + 1 < args.len() {
                        m_dir = Some(args[idx + 1].clone());
                    } else if args[idx] == "--force" {
                        force = true;
                    }
                    idx += 1;
                }
                if let Some(ref dir) = m_dir {
                    if let Some(parent) = Path::new(dir).parent() {
                        std::env::set_var("RUSTDROID_DATA_DIR", parent.to_string_lossy().to_string());
                    }
                }
                let payload = serde_json::json!({
                    "module_id": module_id,
                    "force": force
                }).to_string();
                println!("{}", rustdroid_core::enable_module(&payload));
                std::process::exit(0);
            }
            "disable-module" => {
                if args.len() < 3 {
                    eprintln!("Usage: rustdroid-core-cli disable-module <module_id> [--modules-dir <dir>]");
                    std::process::exit(1);
                }
                let module_id = &args[2];
                let mut m_dir = None;
                let mut idx = 3;
                while idx < args.len() {
                    if args[idx] == "--modules-dir" && idx + 1 < args.len() {
                        m_dir = Some(args[idx + 1].clone());
                        break;
                    }
                    idx += 1;
                }
                if let Some(ref dir) = m_dir {
                    if let Some(parent) = Path::new(dir).parent() {
                        std::env::set_var("RUSTDROID_DATA_DIR", parent.to_string_lossy().to_string());
                    }
                }
                let payload = serde_json::json!({
                    "module_id": module_id
                }).to_string();
                println!("{}", rustdroid_core::disable_module(&payload));
                std::process::exit(0);
            }
            "validate-module-scripts" => {
                if args.len() < 3 {
                    eprintln!("Usage: rustdroid-core-cli validate-module-scripts <module_id> [--modules-dir <dir>]");
                    std::process::exit(1);
                }
                let module_id = &args[2];
                let mut m_dir = None;
                let mut idx = 3;
                while idx < args.len() {
                    if args[idx] == "--modules-dir" && idx + 1 < args.len() {
                        m_dir = Some(args[idx + 1].clone());
                        break;
                    }
                    idx += 1;
                }
                if let Some(ref dir) = m_dir {
                    if let Some(parent) = Path::new(dir).parent() {
                        std::env::set_var("RUSTDROID_DATA_DIR", parent.to_string_lossy().to_string());
                    }
                }
                let payload = serde_json::json!({
                    "module_id": module_id,
                    "modules_dir": m_dir
                }).to_string();
                println!("{}", rustdroid_core::validate_module_scripts(&payload));
                std::process::exit(0);
            }
            "script-plan" => {
                if args.len() < 3 {
                    eprintln!("Usage: rustdroid-core-cli script-plan <module_id> [--modules-dir <dir>]");
                    std::process::exit(1);
                }
                let module_id = &args[2];
                let mut m_dir = None;
                let mut idx = 3;
                while idx < args.len() {
                    if args[idx] == "--modules-dir" && idx + 1 < args.len() {
                        m_dir = Some(args[idx + 1].clone());
                        break;
                    }
                    idx += 1;
                }
                if let Some(ref dir) = m_dir {
                    if let Some(parent) = Path::new(dir).parent() {
                        std::env::set_var("RUSTDROID_DATA_DIR", parent.to_string_lossy().to_string());
                    }
                }
                let payload = serde_json::json!({
                    "module_id": module_id,
                    "modules_dir": m_dir
                }).to_string();
                println!("{}", rustdroid_core::get_module_script_plan(&payload));
                std::process::exit(0);
            }
            "list-module-scripts" => {
                if args.len() < 3 {
                    eprintln!("Usage: rustdroid-core-cli list-module-scripts <module_id> [--modules-dir <dir>]");
                    std::process::exit(1);
                }
                let module_id = &args[2];
                let mut m_dir = None;
                let mut idx = 3;
                while idx < args.len() {
                    if args[idx] == "--modules-dir" && idx + 1 < args.len() {
                        m_dir = Some(args[idx + 1].clone());
                        break;
                    }
                    idx += 1;
                }
                if let Some(ref dir) = m_dir {
                    if let Some(parent) = Path::new(dir).parent() {
                        std::env::set_var("RUSTDROID_DATA_DIR", parent.to_string_lossy().to_string());
                    }
                }
                let payload = serde_json::json!({
                    "module_id": module_id,
                    "modules_dir": m_dir
                }).to_string();
                println!("{}", rustdroid_core::list_module_scripts(&payload));
                std::process::exit(0);
            }
            _ => {}
        }
    }

    // 2. Fall back to standard daemon IPC commands
    let mut data_dir: Option<String> = None;
    let mut socket_path: Option<String> = None;
    let mut action: Option<String> = None;
    let mut extra_arg: Option<String> = None;

    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--data-dir" => {
                if i + 1 < args.len() {
                    data_dir = Some(args[i + 1].clone());
                    i += 1;
                }
            }
            "--socket" => {
                if i + 1 < args.len() {
                    socket_path = Some(args[i + 1].clone());
                    i += 1;
                }
            }
            _ => {
                if action.is_none() {
                    action = Some(args[i].clone());
                } else if extra_arg.is_none() {
                    extra_arg = Some(args[i].clone());
                }
            }
        }
        i += 1;
    }

    if let Some(ref path) = socket_path {
        std::env::set_var("RUSTDROID_SOCKET_PATH", path);
    }
    if let Some(ref dir) = data_dir {
        std::env::set_var("RUSTDROID_DATA_DIR", dir);
        if socket_path.is_none() {
            let inferred_sock = format!("{}/rustdroidd.sock", dir);
            std::env::set_var("RUSTDROID_SOCKET_PATH", inferred_sock);
        }
    }

    let act = match action {
        Some(a) => a,
        None => {
            eprintln!("Usage: rustdroid-core-cli <action> [extra] [--data-dir <dir>] [--socket <socket>]");
            eprintln!("Actions: list-pending, approve-pending <id>, deny-pending <id>, list-policies, get-status");
            eprintln!("Boot Actions: audit <image>, plan <image> --payload <payload_dir>, patch <image> --payload <payload_dir> --output <output.img> [--force], verify <image>");
            std::process::exit(1);
        }
    };

    match act.as_str() {
        "list-pending" => {
            println!("{}", rustdroid_core::list_pending_requests());
        }
        "approve-pending" => {
            let id = match extra_arg {
                Some(ref val) => val.clone(),
                None => {
                    eprintln!("Error: approve-pending requires request_id");
                    std::process::exit(1);
                }
            };
            let payload = serde_json::json!({
                "request_id": id,
                "rule_type": "Always"
            }).to_string();
            println!("{}", rustdroid_core::approve_pending_request(&payload));
        }
        "deny-pending" => {
            let id = match extra_arg {
                Some(ref val) => val.clone(),
                None => {
                    eprintln!("Error: deny-pending requires request_id");
                    std::process::exit(1);
                }
            };
            let payload = serde_json::json!({
                "request_id": id
            }).to_string();
            println!("{}", rustdroid_core::deny_pending_request(&payload));
        }
        "list-policies" => {
            println!("{}", rustdroid_core::list_policies());
        }
        "get-status" => {
            println!("{}", rustdroid_core::get_root_status());
        }
        _ => {
            eprintln!("Unknown action: {}", act);
            std::process::exit(1);
        }
    }
}
