use serde::{Deserialize, Serialize};
use std::fs::{create_dir_all, remove_dir_all, File};
use std::io::{BufRead, BufReader, Read, Write};
use std::path::{Path, PathBuf};
use rustdroid_common::{DISABLE_MODULES_FLAG_NAME, MODULES_DIR_NAME, RustDroidError};
use rustdroid_audit::{log_event, AuditEvent};
use sha2::{Sha256, Digest};

/// Model representing typical module.prop fields
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct ModuleProps {
    pub id: String,
    pub name: String,
    pub version: String,
    #[serde(rename = "versionCode")]
    pub version_code: String,
    pub author: String,
    pub description: String,
    pub min_rustdroid_version: Option<String>,
    pub max_rustdroid_version: Option<String>,
    pub requires_execution: bool,
    pub requires_mounting: bool,
    pub requires_reboot: bool,
}

/// Metadata serialized into module.json
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleInfo {
    pub id: String,
    pub name: String,
    pub version: String,
    pub version_code: String,
    pub author: String,
    pub description: String,
    pub installed_at: u64,
    pub enabled: bool,
    pub safe_mode_disabled: bool,
    pub requires_execution: bool,
    pub requires_mounting: bool,
    pub requires_reboot: bool,
    pub install_source_hash: String,
    pub files_count: usize,
    pub scripts_present: Vec<String>,
    pub safety_scan: String,
    pub warnings: Vec<String>,
    #[serde(default = "default_validation_status")]
    pub script_validation_status: String,
    #[serde(default)]
    pub script_hard_errors_count: usize,
    #[serde(default)]
    pub script_warnings_count: usize,
    #[serde(default)]
    pub script_dry_run_plan_path: String,
}

fn default_validation_status() -> String {
    "unvalidated".to_string()
}

/// State managed in state.json
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleStateJson {
    pub enabled: bool,
    pub safe_mode_disabled: bool,
    pub last_enabled_at: Option<u64>,
    pub last_disabled_at: Option<u64>,
    pub last_error: Option<String>,
    pub boot_stage_execution_enabled: bool,
    pub mounting_enabled: bool,
}

/// Validation output details
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleSafetyReport {
    pub safe: bool,
    pub forbidden_strings_found: Vec<String>,
    pub suspicious_paths_found: Vec<String>,
    pub warnings: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleInstallReport {
    pub success: bool,
    pub error: Option<String>,
    pub warnings: Vec<String>,
    pub module_id: Option<String>,
    pub files_count: usize,
    pub install_log: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleValidationReport {
    pub is_valid: bool,
    pub error: Option<String>,
    pub warnings: Vec<String>,
    pub props: Option<ModuleProps>,
    pub safety_report: ModuleSafetyReport,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleStateReport {
    pub success: bool,
    pub module_id: String,
    pub enabled: bool,
    pub safe_mode_disabled: bool,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleRemoveReport {
    pub success: bool,
    pub module_id: String,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClassifiedAction {
    pub line_number: usize,
    pub line_content: String,
    pub classification: String,
    pub command: String,
    pub is_danger: bool,
    pub is_warning: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModuleScriptInfo {
    pub script_name: String,
    pub exists: bool,
    pub size_bytes: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScriptValidationReport {
    pub script_name: String,
    pub is_valid: bool,
    pub hard_errors: Vec<String>,
    pub warnings: Vec<String>,
    pub classified_actions: Vec<ClassifiedAction>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScriptDryRunPlan {
    pub module_id: String,
    pub scripts_found: Vec<String>,
    pub scripts_valid: bool,
    pub hard_errors: Vec<String>,
    pub warnings: Vec<String>,
    pub classified_actions: Vec<ClassifiedAction>,
    pub boot_stage_order: Vec<String>,
    pub execution_enabled: bool,
    pub mounting_enabled: bool,
    pub dry_run_only: bool,
    pub safe_to_execute_later: bool,
    pub reason: String,
}

pub struct ModuleManager {
    pub modules_dir: PathBuf,
}

impl ModuleManager {
    pub fn new() -> Self {
        Self {
            modules_dir: PathBuf::from(rustdroid_common::get_data_dir()).join(MODULES_DIR_NAME),
        }
    }

    /// Checks if the bootloop safety-mode flag is active
    pub fn is_safe_mode_active(&self) -> bool {
        Path::new(&rustdroid_common::get_data_dir()).join(DISABLE_MODULES_FLAG_NAME).exists()
    }

    /// Set safe mode (write disable flag)
    pub fn set_safe_mode(&self, active: bool) -> Result<(), RustDroidError> {
        let flag_path = Path::new(&rustdroid_common::get_data_dir()).join(DISABLE_MODULES_FLAG_NAME);
        if active {
            if let Some(parent) = flag_path.parent() {
                let _ = create_dir_all(parent);
            }
            File::create(&flag_path)
                .map(|_| ())
                .map_err(|e| RustDroidError::Io(e.to_string()))?;
            let _ = log_event(AuditEvent::DaemonEvent {
                event: "SafeModeEnabled".to_string(),
                details: "Bootloop safeguard triggered - modules disabled.".to_string(),
            });
        } else if flag_path.exists() {
            std::fs::remove_file(&flag_path).map_err(|e| RustDroidError::Io(e.to_string()))?;
            let _ = log_event(AuditEvent::DaemonEvent {
                event: "SafeModeDisabled".to_string(),
                details: "Modules re-enabled.".to_string(),
            });
        }
        Ok(())
    }

    /// Parse property files line-by-line using key=value syntax from file path
    pub fn parse_module_prop(&self, path: &Path) -> Result<ModuleProps, RustDroidError> {
        let mut file = File::open(path).map_err(|e| RustDroidError::Io(e.to_string()))?;
        parse_module_prop_reader(&mut file)
    }

    /// Parse Zip entries and validate module security boundaries
    pub fn validate_module_zip(&self, zip_path: &Path) -> Result<ModuleValidationReport, RustDroidError> {
        let file = File::open(zip_path).map_err(|e| RustDroidError::Io(format!("Failed to open ZIP: {}", e)))?;
        let mut archive = zip::ZipArchive::new(file).map_err(|e| RustDroidError::ModuleFailure(format!("Invalid ZIP archive: {}", e)))?;
        
        let mut warnings = Vec::new();
        let mut errors = Vec::new();
        
        // Find module.prop
        let props = {
            let mut prop_entry = match archive.by_name("module.prop") {
                Ok(entry) => entry,
                Err(_) => {
                    return Ok(ModuleValidationReport {
                        is_valid: false,
                        error: Some("Missing module.prop inside ZIP".to_string()),
                        warnings: Vec::new(),
                        props: None,
                        safety_report: ModuleSafetyReport {
                            safe: false,
                            forbidden_strings_found: Vec::new(),
                            suspicious_paths_found: Vec::new(),
                            warnings: Vec::new(),
                        },
                    });
                }
            };
            match parse_module_prop_reader(&mut prop_entry) {
                Ok(p) => p,
                Err(e) => {
                    return Ok(ModuleValidationReport {
                        is_valid: false,
                        error: Some(format!("Failed to parse module.prop: {}", e)),
                        warnings: Vec::new(),
                        props: None,
                        safety_report: ModuleSafetyReport {
                            safe: false,
                            forbidden_strings_found: Vec::new(),
                            suspicious_paths_found: Vec::new(),
                            warnings: Vec::new(),
                        },
                    });
                }
            }
        };
        
        if let Err(e) = validate_module_id(&props.id) {
            return Ok(ModuleValidationReport {
                is_valid: false,
                error: Some(e),
                warnings: Vec::new(),
                props: Some(props),
                safety_report: ModuleSafetyReport {
                    safe: false,
                    forbidden_strings_found: Vec::new(),
                    suspicious_paths_found: Vec::new(),
                    warnings: Vec::new(),
                },
            });
        }

        let mut safety_report = ModuleSafetyReport {
            safe: true,
            forbidden_strings_found: Vec::new(),
            suspicious_paths_found: Vec::new(),
            warnings: Vec::new(),
        };

        for i in 0..archive.len() {
            let mut entry = archive.by_index(i).map_err(|e| RustDroidError::ModuleFailure(format!("ZIP read error: {}", e)))?;
            let entry_name = entry.name().to_string();
            
            if let Err(e) = validate_zip_entry_path(&entry_name) {
                return Ok(ModuleValidationReport {
                    is_valid: false,
                    error: Some(e),
                    warnings,
                    props: Some(props.clone()),
                    safety_report: ModuleSafetyReport {
                        safe: false,
                        forbidden_strings_found: Vec::new(),
                        suspicious_paths_found: Vec::new(),
                        warnings: Vec::new(),
                    },
                });
            }

            if let Some(mode) = entry.unix_mode() {
                let file_type = mode & 0o170000;
                if file_type == 0o120000 {
                    return Ok(ModuleValidationReport {
                        is_valid: false,
                        error: Some(format!("Forbidden entry type: Symlink detected in ZIP entry '{}'", entry_name)),
                        warnings,
                        props: Some(props.clone()),
                        safety_report: ModuleSafetyReport {
                            safe: false,
                            forbidden_strings_found: Vec::new(),
                            suspicious_paths_found: Vec::new(),
                            warnings: Vec::new(),
                        },
                    });
                }
                if file_type == 0o010000 || file_type == 0o020000 || file_type == 0o060000 || file_type == 0o140000 {
                    return Ok(ModuleValidationReport {
                        is_valid: false,
                        error: Some(format!("Forbidden entry type: Special file (FIFO/device/socket) detected in ZIP entry '{}'", entry_name)),
                        warnings,
                        props: Some(props.clone()),
                        safety_report: ModuleSafetyReport {
                            safe: false,
                            forbidden_strings_found: Vec::new(),
                            suspicious_paths_found: Vec::new(),
                            warnings: Vec::new(),
                        },
                    });
                }
            }
            
            if entry.is_file() {
                let mut content = Vec::new();
                if let Err(e) = (&mut entry).take(10 * 1024 * 1024).read_to_end(&mut content) {
                    return Ok(ModuleValidationReport {
                        is_valid: false,
                        error: Some(format!("Failed to read content of entry '{}': {}", entry_name, e)),
                        warnings,
                        props: Some(props.clone()),
                        safety_report: ModuleSafetyReport {
                            safe: false,
                            forbidden_strings_found: Vec::new(),
                            suspicious_paths_found: Vec::new(),
                            warnings: Vec::new(),
                        },
                    });
                }

                let text = String::from_utf8_lossy(&content);
                let scan_res = run_safety_scan_content(&text);
                
                for forbidden in &scan_res.forbidden_strings_found {
                    if !safety_report.forbidden_strings_found.contains(forbidden) {
                        safety_report.forbidden_strings_found.push(forbidden.clone());
                    }
                }
                for suspicious in &scan_res.suspicious_paths_found {
                    if !safety_report.suspicious_paths_found.contains(suspicious) {
                        safety_report.suspicious_paths_found.push(suspicious.clone());
                    }
                }
                for warning in &scan_res.warnings {
                    if !safety_report.warnings.contains(warning) {
                        safety_report.warnings.push(warning.clone());
                    }
                }
            }
        }

        if !safety_report.forbidden_strings_found.is_empty() {
            safety_report.safe = false;
            errors.push(format!("Forbidden content detected: {:?}", safety_report.forbidden_strings_found));
        }
        
        if !safety_report.suspicious_paths_found.is_empty() {
            warnings.push(format!("Suspicious system paths referenced: {:?}", safety_report.suspicious_paths_found));
        }
        
        for w in &safety_report.warnings {
            warnings.push(w.clone());
        }

        let is_valid = errors.is_empty() && safety_report.safe;
        let error_msg = if errors.is_empty() { None } else { Some(errors.join("; ")) };

        Ok(ModuleValidationReport {
            is_valid,
            error: error_msg,
            warnings,
            props: Some(props),
            safety_report,
        })
    }

    /// Extract zip file securely, generate metadata and state files
    pub fn install_module(&self, zip_path: &Path) -> Result<ModuleInstallReport, RustDroidError> {
        let validation = self.validate_module_zip(zip_path)?;
        let warnings = validation.warnings.clone();
        
        let props = match validation.props {
            Some(p) => p,
            None => {
                return Ok(ModuleInstallReport {
                    success: false,
                    error: Some(format!("Module validation failed: {}", validation.error.unwrap_or_else(|| "Unknown validation error".to_string()))),
                    warnings,
                    module_id: None,
                    files_count: 0,
                    install_log: "Validation failed: No valid module.prop found.".to_string(),
                });
            }
        };

        if !validation.is_valid {
            return Ok(ModuleInstallReport {
                success: false,
                error: Some(format!("Module validation failed: {}", validation.error.unwrap_or_else(|| "Forbidden content or security check failure".to_string()))),
                warnings,
                module_id: Some(props.id),
                files_count: 0,
                install_log: "Validation failed due to safety or format verification errors.".to_string(),
            });
        }

        let module_id = props.id.clone();
        let module_dir = self.modules_dir.join(&module_id);
        
        if module_dir.exists() {
            let _ = remove_dir_all(&module_dir);
        }
        create_dir_all(&module_dir).map_err(|e| RustDroidError::Io(e.to_string()))?;

        let file = File::open(zip_path).map_err(|e| RustDroidError::Io(format!("Failed to open ZIP for extraction: {}", e)))?;
        let mut archive = zip::ZipArchive::new(file).map_err(|e| RustDroidError::ModuleFailure(format!("Failed to read archive: {}", e)))?;

        let mut install_log = String::new();
        install_log.push_str(&format!("Installing module: {}\n", module_id));
        
        let hash = compute_sha256(zip_path).unwrap_or_else(|_| "unknown_hash".to_string());
        install_log.push_str(&format!("Source SHA256: {}\n", hash));

        let mut files_count = 0;

        for i in 0..archive.len() {
            let mut entry = archive.by_index(i).map_err(|e| RustDroidError::ModuleFailure(format!("ZIP entry read failed: {}", e)))?;
            let entry_name = entry.name().to_string();
            
            if let Err(e) = validate_zip_entry_path(&entry_name) {
                let _ = remove_dir_all(&module_dir);
                return Ok(ModuleInstallReport {
                    success: false,
                    error: Some(format!("Zip Slip traversal attempt: {}", e)),
                    warnings,
                    module_id: Some(module_id),
                    files_count: 0,
                    install_log: format!("Extraction aborted: {}", e),
                });
            }

            let entry_path = Path::new(&entry_name);
            let file_name = entry_path.file_name().and_then(|n| n.to_str()).unwrap_or("");
            
            let dest_path = if file_name == "post-fs-data.sh" || file_name == "service.sh" {
                module_dir.join("scripts").join(file_name)
            } else if entry_name == "module.prop" {
                module_dir.join("module.prop")
            } else {
                module_dir.join("files").join(&entry_name)
            };

            if entry.is_dir() {
                let _ = create_dir_all(&dest_path);
            } else {
                if let Some(parent) = dest_path.parent() {
                    create_dir_all(parent).map_err(|e| RustDroidError::Io(e.to_string()))?;
                }
                let mut outfile = File::create(&dest_path).map_err(|e| RustDroidError::Io(e.to_string()))?;
                std::io::copy(&mut entry, &mut outfile).map_err(|e| RustDroidError::Io(e.to_string()))?;
                
                install_log.push_str(&format!("Extracted: {} -> {}\n", entry_name, dest_path.display()));

                if file_name == "post-fs-data.sh" || file_name == "service.sh" {
                    use std::os::unix::fs::PermissionsExt;
                    let _ = std::fs::set_permissions(&dest_path, std::fs::Permissions::from_mode(0o755));
                } else if entry_name != "module.prop" {
                    files_count += 1;
                }
            }
        }

        let scripts_dir = module_dir.join("scripts");
        let mut scripts_present = Vec::new();
        if scripts_dir.exists() {
            if scripts_dir.join("post-fs-data.sh").exists() {
                scripts_present.push("post-fs-data.sh".to_string());
            }
            if scripts_dir.join("service.sh").exists() {
                scripts_present.push("service.sh".to_string());
            }
        }

        let is_safe = self.is_safe_mode_active();
        let installed_at = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);

        let requires_execution = props.requires_execution || !scripts_present.is_empty();

        let info = ModuleInfo {
            id: module_id.clone(),
            name: props.name.clone(),
            version: props.version.clone(),
            version_code: props.version_code.clone(),
            author: props.author.clone(),
            description: props.description.clone(),
            installed_at,
            enabled: false,
            safe_mode_disabled: is_safe,
            requires_execution,
            requires_mounting: props.requires_mounting,
            requires_reboot: props.requires_reboot,
            install_source_hash: hash,
            files_count,
            scripts_present,
            safety_scan: "passed".to_string(),
            warnings: warnings.clone(),
            script_validation_status: "unvalidated".to_string(),
            script_hard_errors_count: 0,
            script_warnings_count: 0,
            script_dry_run_plan_path: "".to_string(),
        };

        let state = ModuleStateJson {
            enabled: false,
            safe_mode_disabled: is_safe,
            last_enabled_at: None,
            last_disabled_at: None,
            last_error: None,
            boot_stage_execution_enabled: false,
            mounting_enabled: false,
        };

        write_json_atomic(&module_dir.join("module.json"), &info)?;
        write_json_atomic(&module_dir.join("state.json"), &state)?;

        // Run script dry-run validator on installation and update module.json automatically
        let _ = self.generate_script_dry_run_plan(&module_id);

        // Also write legacy "disable" file by default since enabled starts false
        let _ = File::create(module_dir.join("disable"));

        install_log.push_str("Installation completed successfully.\n");
        let mut log_file = File::create(module_dir.join("install.log")).map_err(|e| RustDroidError::Io(e.to_string()))?;
        log_file.write_all(install_log.as_bytes()).map_err(|e| RustDroidError::Io(e.to_string()))?;

        let _ = log_event(AuditEvent::ModuleEvent {
            module_id: module_id.clone(),
            action: "install".to_string(),
            success: true,
            details: format!("Successfully installed module: {}, version: {}", module_id, props.version),
        });

        Ok(ModuleInstallReport {
            success: true,
            error: None,
            warnings,
            module_id: Some(module_id),
            files_count,
            install_log,
        })
    }

    /// Load and parse all installed modules from target directory
    pub fn list_modules(&self) -> Result<Vec<ModuleInfo>, RustDroidError> {
        if !self.modules_dir.exists() {
            return Ok(Vec::new());
        }

        let is_safe_mode = self.is_safe_mode_active();
        let mut list = Vec::new();
        let entries = std::fs::read_dir(&self.modules_dir).map_err(|e| RustDroidError::Io(e.to_string()))?;

        for entry in entries.flatten() {
            if entry.path().is_dir() {
                let module_id = entry.file_name().to_string_lossy().to_string();
                if let Ok(info) = self.get_module(&module_id) {
                    let mut modified_info = info;
                    if is_safe_mode {
                        modified_info.safe_mode_disabled = true;
                    }
                    list.push(modified_info);
                }
            }
        }
        Ok(list)
    }

    /// Read module state
    pub fn get_module(&self, module_id: &str) -> Result<ModuleInfo, RustDroidError> {
        let module_dir = self.modules_dir.join(module_id);
        if !module_dir.exists() {
            return Err(RustDroidError::ModuleFailure(format!("Module {} not found", module_id)));
        }

        let json_path = module_dir.join("module.json");
        let state_path = module_dir.join("state.json");

        if !json_path.exists() || !state_path.exists() {
            return Err(RustDroidError::ModuleFailure(format!("Module metadata missing for {}", module_id)));
        }

        let json_content = std::fs::read_to_string(&json_path).map_err(|e| RustDroidError::Io(e.to_string()))?;
        let state_content = std::fs::read_to_string(&state_path).map_err(|e| RustDroidError::Io(e.to_string()))?;

        let mut info: ModuleInfo = serde_json::from_str(&json_content).map_err(|e| RustDroidError::Serialization(e.to_string()))?;
        let state: ModuleStateJson = serde_json::from_str(&state_content).map_err(|e| RustDroidError::Serialization(e.to_string()))?;

        info.enabled = state.enabled;
        info.safe_mode_disabled = state.safe_mode_disabled;
        
        if self.is_safe_mode_active() {
            info.safe_mode_disabled = true;
        }

        Ok(info)
    }

    /// Checks if a module is disabled by checking if a 'disable' file exists inside its directory
    pub fn is_module_disabled(&self, module_id: &str) -> bool {
        self.modules_dir.join(module_id).join("disable").exists()
    }

    /// Toggle module enabled state
    pub fn toggle_module(&self, module_id: &str, enable: bool) -> Result<(), RustDroidError> {
        if enable {
            let _ = self.enable_module(module_id, false)?;
        } else {
            let _ = self.disable_module(module_id)?;
        }
        Ok(())
    }

    /// Enable module state
    pub fn enable_module(&self, module_id: &str, force: bool) -> Result<ModuleStateReport, RustDroidError> {
        let module_dir = self.modules_dir.join(module_id);
        if !module_dir.exists() {
            return Ok(ModuleStateReport {
                success: false,
                module_id: module_id.to_string(),
                enabled: false,
                safe_mode_disabled: false,
                error: Some(format!("Module {} not found", module_id)),
            });
        }

        let is_safe = self.is_safe_mode_active();
        if is_safe && !force {
            return Ok(ModuleStateReport {
                success: false,
                module_id: module_id.to_string(),
                enabled: false,
                safe_mode_disabled: true,
                error: Some("Safe mode is active. Cannot enable modules without force flag.".to_string()),
            });
        }

        let state_path = module_dir.join("state.json");
        let mut state = if state_path.exists() {
            let content = std::fs::read_to_string(&state_path).map_err(|e| RustDroidError::Io(e.to_string()))?;
            serde_json::from_str(&content).unwrap_or(ModuleStateJson {
                enabled: false,
                safe_mode_disabled: is_safe,
                last_enabled_at: None,
                last_disabled_at: None,
                last_error: None,
                boot_stage_execution_enabled: false,
                mounting_enabled: false,
            })
        } else {
            ModuleStateJson {
                enabled: false,
                safe_mode_disabled: is_safe,
                last_enabled_at: None,
                last_disabled_at: None,
                last_error: None,
                boot_stage_execution_enabled: false,
                mounting_enabled: false,
            }
        };

        state.enabled = true;
        state.safe_mode_disabled = is_safe;
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        state.last_enabled_at = Some(now);

        write_json_atomic(&state_path, &state)?;

        // Remove legacy "disable" file
        let disable_file = module_dir.join("disable");
        if disable_file.exists() {
            let _ = std::fs::remove_file(disable_file);
        }

        let _ = log_event(AuditEvent::ModuleEvent {
            module_id: module_id.to_string(),
            action: "enable".to_string(),
            success: true,
            details: format!("Enabled module (force={})", force),
        });

        Ok(ModuleStateReport {
            success: true,
            module_id: module_id.to_string(),
            enabled: true,
            safe_mode_disabled: is_safe,
            error: None,
        })
    }

    /// Disable module state
    pub fn disable_module(&self, module_id: &str) -> Result<ModuleStateReport, RustDroidError> {
        let module_dir = self.modules_dir.join(module_id);
        if !module_dir.exists() {
            return Ok(ModuleStateReport {
                success: false,
                module_id: module_id.to_string(),
                enabled: false,
                safe_mode_disabled: false,
                error: Some(format!("Module {} not found", module_id)),
            });
        }

        let is_safe = self.is_safe_mode_active();
        let state_path = module_dir.join("state.json");
        let mut state = if state_path.exists() {
            let content = std::fs::read_to_string(&state_path).map_err(|e| RustDroidError::Io(e.to_string()))?;
            serde_json::from_str(&content).unwrap_or(ModuleStateJson {
                enabled: false,
                safe_mode_disabled: is_safe,
                last_enabled_at: None,
                last_disabled_at: None,
                last_error: None,
                boot_stage_execution_enabled: false,
                mounting_enabled: false,
            })
        } else {
            ModuleStateJson {
                enabled: false,
                safe_mode_disabled: is_safe,
                last_enabled_at: None,
                last_disabled_at: None,
                last_error: None,
                boot_stage_execution_enabled: false,
                mounting_enabled: false,
            }
        };

        state.enabled = false;
        state.safe_mode_disabled = is_safe;
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        state.last_disabled_at = Some(now);

        write_json_atomic(&state_path, &state)?;

        // Create legacy "disable" file
        let disable_file = module_dir.join("disable");
        let _ = File::create(disable_file);

        let _ = log_event(AuditEvent::ModuleEvent {
            module_id: module_id.to_string(),
            action: "disable".to_string(),
            success: true,
            details: "Disabled module".to_string(),
        });

        Ok(ModuleStateReport {
            success: true,
            module_id: module_id.to_string(),
            enabled: false,
            safe_mode_disabled: is_safe,
            error: None,
        })
    }

    /// Remove module directory entirely
    pub fn uninstall_module(&self, module_id: &str) -> Result<(), RustDroidError> {
        let _ = self.remove_module(module_id)?;
        Ok(())
    }

    /// Uninstall module returning formatted report
    pub fn remove_module(&self, module_id: &str) -> Result<ModuleRemoveReport, RustDroidError> {
        let module_dir = self.modules_dir.join(module_id);
        if !module_dir.exists() {
            return Ok(ModuleRemoveReport {
                success: true,
                module_id: module_id.to_string(),
                error: None,
            });
        }

        if let Err(e) = remove_dir_all(&module_dir) {
            return Ok(ModuleRemoveReport {
                success: false,
                module_id: module_id.to_string(),
                error: Some(format!("Failed to delete module directory: {}", e)),
            });
        }

        let _ = log_event(AuditEvent::ModuleEvent {
            module_id: module_id.to_string(),
            action: "uninstall".to_string(),
            success: true,
            details: "Removed module directory".to_string(),
        });

        Ok(ModuleRemoveReport {
            success: true,
            module_id: module_id.to_string(),
            error: None,
        })
    }

    /// Audits module files recursively
    pub fn scan_module(&self, module_path: &Path) -> Result<ModuleSafetyReport, RustDroidError> {
        if !module_path.exists() {
            return Err(RustDroidError::ModuleFailure(format!("Module path does not exist: {}", module_path.display())));
        }
        let mut safety_report = ModuleSafetyReport {
            safe: true,
            forbidden_strings_found: Vec::new(),
            suspicious_paths_found: Vec::new(),
            warnings: Vec::new(),
        };

        fn scan_dir(dir: &Path, report: &mut ModuleSafetyReport) -> Result<(), RustDroidError> {
            let entries = std::fs::read_dir(dir).map_err(|e| RustDroidError::Io(e.to_string()))?;
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_dir() {
                    scan_dir(&path, report)?;
                } else if path.is_file() {
                    let mut content = Vec::new();
                    if let Ok(mut f) = File::open(&path) {
                        if let Ok(_) = (&mut f).take(10 * 1024 * 1024).read_to_end(&mut content) {
                            let text = String::from_utf8_lossy(&content);
                            let scan_res = run_safety_scan_content(&text);
                            
                            for forbidden in scan_res.forbidden_strings_found {
                                if !report.forbidden_strings_found.contains(&forbidden) {
                                    report.forbidden_strings_found.push(forbidden);
                                }
                            }
                            for suspicious in scan_res.suspicious_paths_found {
                                if !report.suspicious_paths_found.contains(&suspicious) {
                                    report.suspicious_paths_found.push(suspicious);
                                }
                            }
                            for warning in scan_res.warnings {
                                if !report.warnings.contains(&warning) {
                                    report.warnings.push(warning);
                                }
                            }
                        }
                    }
                }
            }
            Ok(())
        }

        scan_dir(module_path, &mut safety_report)?;
        safety_report.safe = safety_report.forbidden_strings_found.is_empty();
        Ok(safety_report)
    }

    /// List script files for a module
    pub fn list_module_scripts(&self, module_id: &str) -> Result<Vec<ModuleScriptInfo>, RustDroidError> {
        let module_dir = self.modules_dir.join(module_id);
        if !module_dir.exists() {
            return Err(RustDroidError::ModuleFailure(format!("Module {} not found", module_id)));
        }
        
        let mut scripts = Vec::new();
        let script_candidates = [
            ("post-fs-data.sh", module_dir.join("scripts").join("post-fs-data.sh"), module_dir.join("post-fs-data.sh")),
            ("service.sh", module_dir.join("scripts").join("service.sh"), module_dir.join("service.sh")),
            ("customize.sh", module_dir.join("scripts").join("customize.sh"), module_dir.join("customize.sh")),
            ("uninstall.sh", module_dir.join("scripts").join("uninstall.sh"), module_dir.join("uninstall.sh")),
        ];

        for (name, path1, path2) in &script_candidates {
            let path = if path1.exists() { path1 } else { path2 };
            let exists = path.exists();
            let size_bytes = if exists {
                std::fs::metadata(path).map(|m| m.len()).unwrap_or(0)
            } else {
                0
            };
            scripts.push(ModuleScriptInfo {
                script_name: name.to_string(),
                exists,
                size_bytes,
            });
        }

        Ok(scripts)
    }

    /// Validate raw content of a module script
    pub fn validate_script_content(&self, script_name: &str, content: &str) -> ScriptValidationReport {
        let mut hard_errors = Vec::new();
        let mut warnings = Vec::new();
        let mut classified_actions = Vec::new();

        for (i, line) in content.lines().enumerate() {
            let line_num = i + 1;
            let (classification, cmd, is_danger, is_warning) = classify_line(line);
            if classification == "Empty" || classification == "Comment" {
                continue;
            }

            let classified = ClassifiedAction {
                line_number: line_num,
                line_content: line.to_string(),
                classification: classification.clone(),
                command: cmd.clone(),
                is_danger,
                is_warning,
            };

            if is_danger {
                hard_errors.push(format!("Line {}: Danger: [{}]: {}", line_num, classification, line.trim()));
            }
            if is_warning {
                warnings.push(format!("Line {}: Warning: [{}]: {}", line_num, classification, line.trim()));
            }

            classified_actions.push(classified);
        }

        let is_valid = hard_errors.is_empty();

        ScriptValidationReport {
            script_name: script_name.to_string(),
            is_valid,
            hard_errors,
            warnings,
            classified_actions,
        }
    }

    /// Validate scripts associated with a module ID
    pub fn validate_module_scripts(&self, module_id: &str) -> Result<ScriptValidationReport, RustDroidError> {
        let module_dir = self.modules_dir.join(module_id);
        if !module_dir.exists() {
            return Err(RustDroidError::ModuleFailure(format!("Module {} not found", module_id)));
        }

        let scripts_info = self.list_module_scripts(module_id)?;
        let mut combined_hard_errors = Vec::new();
        let mut combined_warnings = Vec::new();
        let mut combined_classified_actions = Vec::new();

        for s_info in scripts_info {
            if s_info.exists {
                let path1 = module_dir.join("scripts").join(&s_info.script_name);
                let path2 = module_dir.join(&s_info.script_name);
                let path = if path1.exists() { path1 } else { path2 };

                let content = std::fs::read_to_string(&path)
                    .map_err(|e| RustDroidError::Io(format!("Failed to read script {}: {}", s_info.script_name, e)))?;
                
                let rep = self.validate_script_content(&s_info.script_name, &content);
                for err in rep.hard_errors {
                    combined_hard_errors.push(format!("{}: {}", s_info.script_name, err));
                }
                for warn in rep.warnings {
                    combined_warnings.push(format!("{}: {}", s_info.script_name, warn));
                }
                for act in rep.classified_actions {
                    combined_classified_actions.push(act);
                }
            }
        }

        let is_valid = combined_hard_errors.is_empty();
        Ok(ScriptValidationReport {
            script_name: "Combined Scripts".to_string(),
            is_valid,
            hard_errors: combined_hard_errors,
            warnings: combined_warnings,
            classified_actions: combined_classified_actions,
        })
    }

    /// Generate dry-run script validation report and state verification plan
    pub fn generate_script_dry_run_plan(&self, module_id: &str) -> Result<ScriptDryRunPlan, RustDroidError> {
        let module_dir = self.modules_dir.join(module_id);
        if !module_dir.exists() {
            return Err(RustDroidError::ModuleFailure(format!("Module {} not found", module_id)));
        }

        let scripts_info = self.list_module_scripts(module_id)?;
        let scripts_found: Vec<String> = scripts_info.iter().filter(|s| s.exists).map(|s| s.script_name.clone()).collect();

        let validation = self.validate_module_scripts(module_id)?;
        let scripts_valid = validation.is_valid;
        let safe_to_execute_later = scripts_valid;

        let reason = if !scripts_valid {
            format!("Rejected due to {} hard errors", validation.hard_errors.len())
        } else if scripts_found.is_empty() {
            "No scripts found".to_string()
        } else {
            "Dry-run verification completed successfully".to_string()
        };

        let plan = ScriptDryRunPlan {
            module_id: module_id.to_string(),
            scripts_found,
            scripts_valid,
            hard_errors: validation.hard_errors,
            warnings: validation.warnings,
            classified_actions: validation.classified_actions,
            boot_stage_order: vec!["post-fs-data.sh".to_string(), "service.sh".to_string()],
            execution_enabled: false,
            mounting_enabled: false,
            dry_run_only: true,
            safe_to_execute_later,
            reason,
        };

        let plan_path = module_dir.join("script_dry_run_plan.json");
        write_json_atomic(&plan_path, &plan)?;

        // Also update module.json with script details and plan path
        let info_path = module_dir.join("module.json");
        if info_path.exists() {
            let json_content = std::fs::read_to_string(&info_path).map_err(|e| RustDroidError::Io(e.to_string()))?;
            let mut info: ModuleInfo = serde_json::from_str(&json_content).map_err(|e| RustDroidError::Serialization(e.to_string()))?;
            
            info.script_validation_status = if !plan.scripts_valid {
                "rejected".to_string()
            } else if plan.scripts_found.is_empty() {
                "none".to_string()
            } else if !plan.warnings.is_empty() {
                "warnings".to_string()
            } else {
                "verified".to_string()
            };
            
            info.script_hard_errors_count = plan.hard_errors.len();
            info.script_warnings_count = plan.warnings.len();
            info.script_dry_run_plan_path = plan_path.to_string_lossy().to_string();
            
            write_json_atomic(&info_path, &info)?;
        }

        Ok(plan)
    }

    /// Helper to verify post-patch image status
    pub fn dummy_validation_placeholder(&self) {}

    pub fn execute_boot_stage(&self, stage: &str) -> Result<(), RustDroidError> {
        let _ = log_event(AuditEvent::DaemonEvent {
            event: "BootStageSkip".to_string(),
            details: format!("Skipped {} because script execution is disabled in v1.3 (dry-run only)", stage),
        });
        Ok(())
    }
}

/// Helper function to parse module ID rules
pub fn validate_module_id(id: &str) -> Result<(), String> {
    if id.is_empty() || id.trim().is_empty() {
        return Err("Module ID is empty or whitespace-only".to_string());
    }
    for c in id.chars() {
        if !c.is_ascii_alphanumeric() && c != '_' && c != '-' && c != '.' {
            return Err(format!("Invalid character '{}' in module ID '{}'", c, id));
        }
    }
    Ok(())
}

/// Helper function to validate entry paths against Zip Slip path traversal vulnerabilities
pub fn validate_zip_entry_path(path_str: &str) -> Result<(), String> {
    let path = Path::new(path_str);
    if path.is_absolute() {
        return Err(format!("Absolute path detected in ZIP entry: {}", path_str));
    }
    for component in path.components() {
        match component {
            std::path::Component::ParentDir => {
                return Err(format!("Path traversal ('..') detected in ZIP entry: {}", path_str));
            }
            std::path::Component::RootDir => {
                return Err(format!("Root directory indicator detected in ZIP entry: {}", path_str));
            }
            std::path::Component::Prefix(_) => {
                return Err(format!("Windows prefix detected in ZIP entry: {}", path_str));
            }
            _ => {}
        }
    }
    if path_str.contains('\\') {
        return Err(format!("Backslash character detected in ZIP entry path: {}", path_str));
    }
    if path_str.contains('\0') {
        return Err(format!("Null byte detected in ZIP entry path: {}", path_str));
    }
    Ok(())
}

/// Helper to run the safety audit on string contents
pub fn run_safety_scan_content(text: &str) -> ModuleSafetyReport {
    let mut forbidden_strings_found = Vec::new();
    let mut suspicious_paths_found = Vec::new();
    let mut warnings = Vec::new();

    let forbidden_keys = [
        "setenforce",
        "getenforce 0",
        "bypass",
        "attestation",
        "play integrity",
        "root hiding",
        "hide root",
        "kprobe",
        "syscall hook",
        "/dev/block",
        "fastboot",
        "reboot",
        "pivot_root",
    ];

    let suspicious_keys = [
        "/system",
        "/vendor",
        "/product",
        "/system_ext",
        "/odm",
        "/dev",
        "/proc",
        "/sys",
    ];

    let text_lower = text.to_lowercase();
    for key in &forbidden_keys {
        if text_lower.contains(key) {
            forbidden_strings_found.push(key.to_string());
        }
    }

    for key in &suspicious_keys {
        if text_lower.contains(key) {
            suspicious_paths_found.push(key.to_string());
        }
    }

    if text_lower.contains("setenforce 0") || text_lower.contains("setenforce off") {
        warnings.push("Attempt to disable SELinux detected.".to_string());
    }

    let safe = forbidden_strings_found.is_empty();

    ModuleSafetyReport {
        safe,
        forbidden_strings_found,
        suspicious_paths_found,
        warnings,
    }
}

/// Helper to compute ZIP SHA-256
pub fn compute_sha256(path: &Path) -> Result<String, std::io::Error> {
    let mut file = File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0; 4096];
    loop {
        let count = file.read(&mut buffer)?;
        if count == 0 { break; }
        hasher.update(&buffer[..count]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

/// Write JSON payload safely and atomically
pub fn write_json_atomic<T: Serialize>(path: &Path, data: &T) -> Result<(), RustDroidError> {
    let tmp_path = path.with_extension("tmp");
    let content = serde_json::to_string_pretty(data).map_err(|e| RustDroidError::Serialization(e.to_string()))?;
    
    let mut file = File::create(&tmp_path).map_err(|e| RustDroidError::Io(e.to_string()))?;
    file.write_all(content.as_bytes()).map_err(|e| RustDroidError::Io(e.to_string()))?;
    file.sync_all().map_err(|e| RustDroidError::Io(e.to_string()))?;
    
    std::fs::rename(&tmp_path, path).map_err(|e| RustDroidError::Io(e.to_string()))?;
    Ok(())
}

/// Parse properties from an abstract reader
pub fn parse_module_prop_reader<R: Read>(reader: &mut R) -> Result<ModuleProps, RustDroidError> {
    let buf_reader = BufReader::new(reader);
    let mut props = ModuleProps::default();

    for line_res in buf_reader.lines() {
        let line = line_res.map_err(|e| RustDroidError::Io(e.to_string()))?;
        let trimmed = line.trim();
        if trimmed.starts_with('#') || !trimmed.contains('=') {
            continue;
        }

        let parts: Vec<&str> = trimmed.splitn(2, '=').collect();
        let key = parts[0].trim();
        let val = parts[1].trim().to_string();

        match key {
            "id" => props.id = val,
            "name" => props.name = val,
            "version" => props.version = val,
            "versionCode" => props.version_code = val,
            "author" => props.author = val,
            "description" => props.description = val,
            "minRustDroidVersion" => props.min_rustdroid_version = Some(val),
            "maxRustDroidVersion" => props.max_rustdroid_version = Some(val),
            "requiresExecution" => props.requires_execution = val.to_lowercase() == "true",
            "requiresMounting" => props.requires_mounting = val.to_lowercase() == "true",
            "requiresReboot" => props.requires_reboot = val.to_lowercase() == "true",
            _ => {}
        }
    }

    if props.id.is_empty() {
        return Err(RustDroidError::ModuleFailure("Missing module ID in module.prop".to_string()));
    }

    Ok(props)
}

/// Helper function to classify script lines
pub fn classify_line(line: &str) -> (String, String, bool, bool) {
    let trimmed = line.trim();
    if trimmed.is_empty() {
        return ("Empty".to_string(), "".to_string(), false, false);
    }
    if trimmed.starts_with('#') {
        return ("Comment".to_string(), "".to_string(), false, false);
    }

    let cmd = trimmed.split_whitespace().next().unwrap_or("");
    let cmd_lower = cmd.to_lowercase();
    let line_lower = trimmed.to_lowercase();

    // Check dangerous strings first!
    if line_lower.contains("setenforce") {
        return ("SELinux Modification Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("supolicy") || line_lower.contains("magiskpolicy") {
        return ("SELinux Policy Modification Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("/sys/fs/selinux/enforce") {
        return ("SELinux Direct Bypass Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("/dev/block") {
        return ("Block Device Write Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("dd if=") || line_lower.contains("dd of=") {
        return ("Raw Write Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("fastboot") {
        return ("Bootloader Command Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("reboot") || line_lower.contains("svc power reboot") {
        return ("Reboot Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("pivot_root") {
        return ("Namespace Modification Attempt (pivot_root)".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("mount") {
        if line_lower.contains("-o bind") || line_lower.contains("bind") {
            return ("Bind Mount Attempt".to_string(), cmd.to_string(), true, false);
        }
        return ("Mount Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("overlayfs") {
        return ("OverlayFS Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("insmod") || line_lower.contains("rmmod") {
        return ("Kernel Module Modification Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("kprobe") {
        return ("Kernel Probe Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("syscall") {
        return ("System Call Hook Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("attestation") || line_lower.contains("play integrity") {
        return ("Play Integrity / Attestation Manipulation".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("root hiding") || line_lower.contains("hide root") {
        return ("Root/Bypass Hiding Attempt".to_string(), cmd.to_string(), true, false);
    }
    if line_lower.contains("resetprop") {
        let suspicious = ["attestation", "verifiedboot", "vbmeta", "bootloader", "unlock", "hiding", "hide", "fingerprint", "security"];
        for s in &suspicious {
            if line_lower.contains(s) {
                return ("Suspicious Property Reset Attempt".to_string(), cmd.to_string(), true, false);
            }
        }
    }

    if line_lower.contains("sh -c") {
        return ("Shell Execution Attempt".to_string(), cmd.to_string(), false, true);
    }

    // Now warnings/suspicious commands:
    if line_lower.contains("curl") || line_lower.contains("wget") || line_lower.contains("nc ") || line_lower.contains("netcat") {
        return ("Network Attempt".to_string(), cmd.to_string(), false, true);
    }
    if line_lower.contains(">") || line_lower.contains(">>") || line_lower.contains("tee") {
        let system_paths = ["/system", "/vendor", "/product", "/system_ext", "/odm"];
        for p in &system_paths {
            if line_lower.contains(p) {
                return ("System Path Write Attempt".to_string(), cmd.to_string(), false, true);
            }
        }
        return ("File Write".to_string(), cmd.to_string(), false, false);
    }

    match cmd_lower.as_str() {
        "echo" | "log" | "printf" => {
            ("Echo/Logging".to_string(), cmd.to_string(), false, false)
        }
        "cat" | "read" | "head" | "tail" | "grep" => {
            ("File Read".to_string(), cmd.to_string(), false, false)
        }
        "mkdir" => {
            ("Directory Create".to_string(), cmd.to_string(), false, false)
        }
        "chmod" => {
            ("Permission Change".to_string(), cmd.to_string(), false, true)
        }
        "chown" | "chgrp" => {
            ("Ownership Change".to_string(), cmd.to_string(), false, true)
        }
        "getprop" => {
            ("Property Read".to_string(), cmd.to_string(), false, false)
        }
        "setprop" => {
            ("Property Set".to_string(), cmd.to_string(), false, true)
        }
        "start" | "stop" | "init" => {
            ("Service Control".to_string(), cmd.to_string(), false, true)
        }
        "kill" | "pkill" | "killall" => {
            ("Process Control".to_string(), cmd.to_string(), false, true)
        }
        "am" | "pm" | "app_process" | "sh" => {
            ("Android Utility/Shell Start".to_string(), cmd.to_string(), false, true)
        }
        "toybox" | "busybox" => {
            ("Toolbox Invocation".to_string(), cmd.to_string(), false, true)
        }
        _ => {
            let standard_safes = ["cp", "mv", "rm", "cd", "pwd", "export", "local", "return", "exit", "sleep", "touch", "ln", "find", "uname"];
            if standard_safes.contains(&cmd_lower.as_str()) {
                ("Process Start (Safe)".to_string(), cmd.to_string(), false, false)
            } else {
                ("Unknown Command".to_string(), cmd.to_string(), false, true)
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::{remove_dir_all, remove_file};
    use std::io::Write;

    static TEST_MUTEX: std::sync::Mutex<()> = std::sync::Mutex::new(());

    fn create_test_zip(path: &Path, files: &[(&str, &str)]) -> std::io::Result<()> {
        let file = File::create(path)?;
        let mut zip = zip::ZipWriter::new(file);
        let options = zip::write::FileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated);
            
        for &(name, content) in files {
            zip.start_file(name, options)?;
            zip.write_all(content.as_bytes())?;
        }
        zip.finish()?;
        Ok(())
    }

    fn create_symlink_zip(path: &Path) -> std::io::Result<()> {
        let file = File::create(path)?;
        let mut zip = zip::ZipWriter::new(file);
        let options = zip::write::FileOptions::default();
        zip.start_file("module.prop", options)?;
        zip.write_all(b"id=test\nname=Test\nversion=1.0\nversionCode=1\n")?;
        zip.add_symlink("some_symlink", "target_file", options)?;
        zip.finish()?;
        Ok(())
    }

    fn create_special_file_zip(path: &Path) -> std::io::Result<()> {
        let file = File::create(path)?;
        let mut zip = zip::ZipWriter::new(file);
        let options = zip::write::FileOptions::default();
        zip.start_file("module.prop", options)?;
        zip.write_all(b"id=test\nname=Test\nversion=1.0\nversionCode=1\n")?;
        zip.start_file("some_fifo", options)?;
        zip.write_all(b"fifo_data")?;
        zip.finish()?;
        
        // Read zip file bytes and patch the central directory headers to construct a real FIFO entry.
        let mut bytes = std::fs::read(path)?;
        for i in 0..bytes.len() - 4 {
            if &bytes[i..i+4] == &[0x50, 0x4b, 0x01, 0x02] {
                let ext_attr_offset = i + 38;
                if ext_attr_offset + 4 <= bytes.len() {
                    // System code: 3 (Unix)
                    bytes[i + 5] = 3;
                    // Mode: S_IFIFO (0o010000 => 0x1000) -> 0x1000644
                    bytes[ext_attr_offset] = 0x00;
                    bytes[ext_attr_offset + 1] = 0x00;
                    bytes[ext_attr_offset + 2] = 0xa4; // rw-r--r--
                    bytes[ext_attr_offset + 3] = 0x10; // FIFO (0x1000)
                }
            }
        }
        std::fs::write(path, bytes)?;
        Ok(())
    }

    #[test]
    fn test_parse_props() {
        let temp_prop = "./test_module.prop";
        let mut file = File::create(temp_prop).unwrap();
        writeln!(file, "id=test-module").unwrap();
        writeln!(file, "name=Unit Tester").unwrap();
        writeln!(file, "version=v1.0").unwrap();
        writeln!(file, "versionCode=1").unwrap();
        writeln!(file, "author=DeepMind").unwrap();
        writeln!(file, "description=Test verification").unwrap();
        writeln!(file, "requiresMounting=true").unwrap();

        let manager = ModuleManager {
            modules_dir: PathBuf::from("./"),
        };
        let p = manager.parse_module_prop(Path::new(temp_prop)).unwrap();
        assert_eq!(p.id, "test-module");
        assert_eq!(p.name, "Unit Tester");
        assert_eq!(p.author, "DeepMind");
        assert_eq!(p.requires_mounting, true);

        let _ = remove_file(temp_prop);
    }

    #[test]
    fn test_missing_id_rejected() {
        let temp_prop = "./test_missing_id.prop";
        let mut file = File::create(temp_prop).unwrap();
        writeln!(file, "name=Unit Tester").unwrap();
        
        let manager = ModuleManager {
            modules_dir: PathBuf::from("./"),
        };
        let res = manager.parse_module_prop(Path::new(temp_prop));
        assert!(res.is_err());
        let _ = remove_file(temp_prop);
    }

    #[test]
    fn test_invalid_module_id() {
        assert!(validate_module_id("").is_err());
        assert!(validate_module_id("  ").is_err());
        assert!(validate_module_id("id/with/slash").is_err());
        assert!(validate_module_id("id\\with\\backslash").is_err());
        assert!(validate_module_id("id_with_space ").is_err());
        assert!(validate_module_id("id\0null").is_err());
        assert!(validate_module_id("valid-id.123_ABC").is_ok());
    }

    #[test]
    fn test_path_traversal_zip_rejected() {
        let zip_path = Path::new("./test_traversal.zip");
        let files = [
            ("module.prop", "id=test\nname=Test\nversion=1.0\nversionCode=1\n"),
            ("../outside_file", "malicious content"),
        ];
        create_test_zip(zip_path, &files).unwrap();

        let manager = ModuleManager {
            modules_dir: PathBuf::from("./test_modules_root"),
        };
        let report = manager.validate_module_zip(zip_path).unwrap();
        assert!(!report.is_valid);
        assert!(report.error.unwrap().contains("traversal"));

        let _ = remove_file(zip_path);
    }

    #[test]
    fn test_absolute_path_zip_rejected() {
        let zip_path = Path::new("./test_absolute.zip");
        let files = [
            ("module.prop", "id=test\nname=Test\nversion=1.0\nversionCode=1\n"),
            ("/etc/passwd", "malicious content"),
        ];
        create_test_zip(zip_path, &files).unwrap();

        let manager = ModuleManager {
            modules_dir: PathBuf::from("./test_modules_root"),
        };
        let report = manager.validate_module_zip(zip_path).unwrap();
        assert!(!report.is_valid);
        assert!(report.error.unwrap().contains("Absolute"));

        let _ = remove_file(zip_path);
    }

    #[test]
    fn test_symlink_entry_rejected() {
        let zip_path = Path::new("./test_symlink.zip");
        create_symlink_zip(zip_path).unwrap();

        let manager = ModuleManager {
            modules_dir: PathBuf::from("./test_modules_root"),
        };

        let report = manager.validate_module_zip(zip_path).unwrap();
        assert!(!report.is_valid);
        assert!(report.error.unwrap().contains("Symlink"));

        let _ = remove_file(zip_path);
    }

    #[test]
    fn test_special_file_entry_rejected() {
        let zip_path = Path::new("./test_special.zip");
        create_special_file_zip(zip_path).unwrap();

        let manager = ModuleManager {
            modules_dir: PathBuf::from("./test_modules_root"),
        };
        let report = manager.validate_module_zip(zip_path).unwrap();
        assert!(!report.is_valid);
        assert!(report.error.unwrap().contains("Special file"));

        let _ = remove_file(zip_path);
    }

    #[test]
    fn test_forbidden_strings_detection() {
        let _lock = TEST_MUTEX.lock().unwrap();
        let keys = [
            "setenforce",
            "/dev/block",
            "bypass",
            "attestation",
            "root hiding",
            "hide root",
            "kprobe",
            "syscall hook",
            "pivot_root",
            "fastboot",
            "reboot",
        ];

        for key in &keys {
            let res = run_safety_scan_content(&format!("some text before {} some text after", key));
            assert!(!res.safe, "Should have rejected string with key: {}", key);
            assert!(
                res.forbidden_strings_found.iter().any(|f| f.to_lowercase() == *key),
                "Missing key {} in forbidden_strings_found: {:?}",
                key,
                res.forbidden_strings_found
            );
        }

        // Test ZIP installation rejection for each of these forbidden strings
        let temp_data_dir = "./test_forbidden_install_data";
        std::env::set_var("RUSTDROID_DATA_DIR", temp_data_dir);
        let _ = remove_dir_all(temp_data_dir);
        let manager = ModuleManager::new();

        for key in &keys {
            let zip_path = Path::new("./test_forbidden_val.zip");
            let _ = remove_file(zip_path);

            let files = [
                ("module.prop", "id=forbidden-test\nname=Forbidden Test\nversion=v1.2\nversionCode=12\nauthor=Tester\ndescription=Forbidden test\n"),
                ("scripts/post-fs-data.sh", &format!("echo 'Testing'\n{}\n", key)),
            ];
            create_test_zip(zip_path, &files).unwrap();

            let report = manager.install_module(zip_path);
            assert!(
                report.is_err() || !report.as_ref().unwrap().success,
                "Installation should have failed for zip containing forbidden string: {}",
                key
            );

            let _ = remove_file(zip_path);
        }
        let _ = remove_dir_all(temp_data_dir);
    }

    #[test]
    fn test_installation_lifecycle() {
        let _lock = TEST_MUTEX.lock().unwrap();
        let temp_data_dir = "./test_lifetime_data";
        std::env::set_var("RUSTDROID_DATA_DIR", temp_data_dir);
        let _ = remove_dir_all(temp_data_dir);

        let manager = ModuleManager::new();

        let zip_path = Path::new("./test_valid_module.zip");
        let files = [
            ("module.prop", "id=hello-test\nname=Hello Test\nversion=v1.2\nversionCode=12\nauthor=Tester\ndescription=Lifecycles\n"),
            ("scripts/post-fs-data.sh", "echo 'hello'"),
            ("files/system/bin/hello", "binary data"),
        ];
        create_test_zip(zip_path, &files).unwrap();

        let report = manager.install_module(zip_path).unwrap();
        assert!(report.success);
        assert_eq!(report.module_id.unwrap(), "hello-test");
        assert_eq!(report.files_count, 1); // Only system/bin/hello counts

        // verify module.json and state.json exist
        let m_dir = manager.modules_dir.join("hello-test");
        assert!(m_dir.join("module.json").exists());
        assert!(m_dir.join("state.json").exists());
        assert!(m_dir.join("install.log").exists());

        // Check defaults
        let info = manager.get_module("hello-test").unwrap();
        assert_eq!(info.enabled, false);
        assert_eq!(info.requires_execution, true); // has scripts/post-fs-data.sh

        // Enable module
        let state_rep = manager.enable_module("hello-test", false).unwrap();
        assert!(state_rep.success);
        assert_eq!(state_rep.enabled, true);

        // Check toggled status
        let info_enabled = manager.get_module("hello-test").unwrap();
        assert_eq!(info_enabled.enabled, true);

        // Disable module
        let state_rep2 = manager.disable_module("hello-test").unwrap();
        assert!(state_rep2.success);
        assert_eq!(state_rep2.enabled, false);

        // List modules
        let list = manager.list_modules().unwrap();
        assert_eq!(list.len(), 1);
        assert_eq!(list[0].id, "hello-test");

        // Safe mode override
        manager.set_safe_mode(true).unwrap();
        let list_safe = manager.list_modules().unwrap();
        assert_eq!(list_safe[0].safe_mode_disabled, true);

        // Try enabling under safe mode (no force) -> should fail
        let enable_fail = manager.enable_module("hello-test", false).unwrap();
        assert!(!enable_fail.success);

        // Try enabling under safe mode with force -> should succeed
        let enable_force = manager.enable_module("hello-test", true).unwrap();
        assert!(enable_force.success);

        manager.set_safe_mode(false).unwrap();

        // Remove module
        let remove_rep = manager.remove_module("hello-test").unwrap();
        assert!(remove_rep.success);
        assert!(!m_dir.exists());

        let _ = remove_file(zip_path);
        let _ = remove_dir_all(temp_data_dir);
    }

    #[test]
    fn test_script_classification_rules() {
        // Comment/empty
        let (c, _, d, w) = classify_line("  # this is comment ");
        assert_eq!(c, "Comment");
        assert!(!d);
        assert!(!w);

        let (c, _, d, w) = classify_line("   ");
        assert_eq!(c, "Empty");
        assert!(!d);
        assert!(!w);

        // safe logging
        let (c, _, d, w) = classify_line("echo 'hi'");
        assert_eq!(c, "Echo/Logging");
        assert!(!d && !w);

        // warning perm change
        let (c, _, d, w) = classify_line("chmod 755 /data");
        assert_eq!(c, "Permission Change");
        assert!(!d && w);

        // warning setprop
        let (c, _, d, w) = classify_line("setprop debug.rust 1");
        assert_eq!(c, "Property Set");
        assert!(!d && w);

        // mount error
        let (c, _, d, w) = classify_line("mount /dev/block/boot /system");
        assert_eq!(c, "Block Device Write Attempt"); // dev/block takes precedence
        assert!(d && !w);

        let (c, _, d, w) = classify_line("mount -o bind /data/a /data/b");
        assert_eq!(c, "Bind Mount Attempt");
        assert!(d && !w);

        let (c, _, d, w) = classify_line("mount /system");
        assert_eq!(c, "Mount Attempt");
        assert!(d && !w);

        // block device write
        let (c, _, d, w) = classify_line("dd if=/dev/block/by-name/boot of=/sdcard/boot.img");
        assert_eq!(c, "Block Device Write Attempt");
        assert!(d && !w);

        // setenforce error
        let (c, _, d, w) = classify_line("setenforce 0");
        assert_eq!(c, "SELinux Modification Attempt");
        assert!(d && !w);

        // reboot/fastboot error
        let (c, _, d, w) = classify_line("reboot");
        assert_eq!(c, "Reboot Attempt");
        assert!(d && !w);

        let (c, _, d, w) = classify_line("fastboot oem unlock");
        assert_eq!(c, "Bootloader Command Attempt");
        assert!(d && !w);

        // attestation error
        let (c, _, d, w) = classify_line("play integrity bypass");
        assert_eq!(c, "Play Integrity / Attestation Manipulation");
        assert!(d && !w);

        // root hiding error
        let (c, _, d, w) = classify_line("hide root tool");
        assert_eq!(c, "Root/Bypass Hiding Attempt");
        assert!(d && !w);

        // kprobe / syscall
        let (c, _, d, w) = classify_line("kprobe hooking");
        assert_eq!(c, "Kernel Probe Attempt");
        assert!(d && !w);

        let (c, _, d, w) = classify_line("syscall hook");
        assert_eq!(c, "System Call Hook Attempt");
        assert!(d && !w);

        // unknown command produes warning
        let (c, _, d, w) = classify_line("some_weird_unknown_utility args");
        assert_eq!(c, "Unknown Command");
        assert!(!d && w);
    }

    #[test]
    fn test_script_dry_run_plan_lifecycle() {
        let _lock = TEST_MUTEX.lock().unwrap();
        let temp_data_dir = "./test_script_lifetime_data";
        std::env::set_var("RUSTDROID_DATA_DIR", temp_data_dir);
        let _ = remove_dir_all(temp_data_dir);

        let manager = ModuleManager::new();

        // 1. Create a module with scripts
        let zip_path = Path::new("./test_valid_module_with_scripts.zip");
        let files = [
            ("module.prop", "id=hello-scripts\nname=Hello Scripts\nversion=v1.3\nversionCode=13\nauthor=Tester\ndescription=Script Validation\n"),
            ("scripts/post-fs-data.sh", "echo 'starting'\nchmod 755 /data/bin\n"),
            ("scripts/service.sh", "echo 'service'\nsome_weird_utility\n"),
            ("files/system/bin/dummy", "data"),
        ];
        create_test_zip(zip_path, &files).unwrap();

        // Install module should auto-generate the plan and update module.json
        let report = manager.install_module(zip_path).unwrap();
        assert!(report.success);

        // verify script_dry_run_plan.json exists
        let m_dir = manager.modules_dir.join("hello-scripts");
        assert!(m_dir.join("script_dry_run_plan.json").exists());

        // Check plan contents
        let plan_path = m_dir.join("script_dry_run_plan.json");
        let plan_content = std::fs::read_to_string(&plan_path).unwrap();
        let plan: ScriptDryRunPlan = serde_json::from_str(&plan_content).unwrap();

        assert_eq!(plan.module_id, "hello-scripts");
        assert_eq!(plan.scripts_found.len(), 2);
        assert!(plan.scripts_valid); // No hard errors, only warnings
        assert!(plan.safe_to_execute_later);

        // Verify module.json updated
        let info = manager.get_module("hello-scripts").unwrap();
        assert_eq!(info.script_validation_status, "warnings");
        assert_eq!(info.script_hard_errors_count, 0);
        assert_eq!(info.script_warnings_count, 2); // chmod and unknown command
        assert!(!info.script_dry_run_plan_path.is_empty());

        // 2. Now test with a dangerous script
        let dangerous_zip_path = Path::new("./test_dangerous_module.zip");
        let dangerous_files = [
            ("module.prop", "id=danger-scripts\nname=Danger Scripts\nversion=v1.3\nversionCode=13\nauthor=Tester\ndescription=Dangerous scripts\n"),
            ("scripts/post-fs-data.sh", "echo 'unsafe'\nmount -o bind /data/a /data/b\n"),
        ];
        create_test_zip(dangerous_zip_path, &dangerous_files).unwrap();

        let report2 = manager.install_module(dangerous_zip_path).unwrap();
        assert!(report2.success);

        let m_dir2 = manager.modules_dir.join("danger-scripts");
        let plan_path2 = m_dir2.join("script_dry_run_plan.json");
        let plan_content2 = std::fs::read_to_string(&plan_path2).unwrap();
        let plan2: ScriptDryRunPlan = serde_json::from_str(&plan_content2).unwrap();

        assert!(!plan2.scripts_valid);
        assert!(!plan2.safe_to_execute_later);
        assert_eq!(plan2.hard_errors.len(), 1);

        let info2 = manager.get_module("danger-scripts").unwrap();
        assert_eq!(info2.script_validation_status, "rejected");
        assert_eq!(info2.script_hard_errors_count, 1);

        let _ = remove_file(zip_path);
        let _ = remove_file(dangerous_zip_path);
        let _ = remove_dir_all(temp_data_dir);
    }

    #[test]
    fn test_static_code_constraints() {
        let manifest_dir = std::env::var("CARGO_MANIFEST_DIR").unwrap_or_else(|_| ".".to_string());
        let src_path = Path::new(&manifest_dir).join("src/lib.rs");
        assert!(src_path.exists(), "Source file lib.rs must exist");
        
        let content = std::fs::read_to_string(&src_path).unwrap();
        
        // Construct search patterns dynamically to avoid matching our own test code
        let cmd_new_pat = format!("{}{}", "Command::", "new");
        let std_cmd_pat = format!("{}{}", "std::process::", "Command");
        let nix_mount_pat = format!("{}{}", "nix::", "mount");
        let libc_mount_pat = format!("{}{}", "libc::", "mount");
        let pivot_root_pat = format!("{}{}", "pivot_root", "(");
        let reboot_pat = format!("{}{}", "reboot", "(");

        // Ensure no shell command execution is present in rustdroid-module src
        let err1 = format!("rustdroid-module must not spawn shell/processes using {}{}", "Command::", "new");
        let err2 = format!("rustdroid-module must not reference {}{}", "std::process::", "Command");
        assert!(!content.contains(&cmd_new_pat), "{}", err1);
        assert!(!content.contains(&std_cmd_pat), "{}", err2);

        // Ensure no mount system calls or raw mount library calls are present
        let err3 = format!("rustdroid-module must not call {}{}", "nix::", "mount");
        let err4 = format!("rustdroid-module must not call {}{}", "libc::", "mount");
        assert!(!content.contains(&nix_mount_pat), "{}", err3);
        assert!(!content.contains(&libc_mount_pat), "{}", err4);
        
        // Ensure no pivot_root system/library calls are present
        let err5 = format!("rustdroid-module must not call {}{}", "pivot_root", "(");
        assert!(!content.contains(&pivot_root_pat), "{}", err5);
        
        // Ensure no reboot system/library calls are present
        let err6 = format!("rustdroid-module must not call {}{}", "reboot", "(");
        assert!(!content.contains(&reboot_pat), "{}", err6);
    }
}
