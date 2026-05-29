use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::fs::{create_dir_all, File};
use std::io::{Read, Write};
use std::path::Path;
use rustdroid_common::{
    POLICY_FILE_PATH, RustDroidError, PolicyEntry, SuDecision, PolicyRuleType,
    ExecutionPolicy
};
use rustdroid_audit::{log_event, AuditEvent};

fn default_migration_version() -> u32 {
    2
}

/// Root database of policies
#[derive(Debug, Serialize, Deserialize)]
pub struct PolicyDatabase {
    #[serde(default = "default_migration_version")]
    pub migration_version: u32,
    pub rules: HashMap<u32, PolicyEntry>,
}

impl Default for PolicyDatabase {
    fn default() -> Self {
        Self {
            migration_version: 2,
            rules: HashMap::new(),
        }
    }
}

pub struct PolicyEngine {
    pub db: PolicyDatabase,
    file_path: String,
    pub allow_uid_1000: bool,
}

impl PolicyEngine {
    /// Instantiate a new PolicyEngine linked to a JSON database
    pub fn new() -> Self {
        Self::with_path(POLICY_FILE_PATH)
    }

    pub fn with_path(path: &str) -> Self {
        let mut engine = PolicyEngine {
            db: PolicyDatabase::default(),
            file_path: path.to_string(),
            allow_uid_1000: false,
        };
        let _ = engine.load();
        engine
    }

    /// Load policy database safely from disk.
    /// Handles missing directory creation and corrupt file recovery.
    pub fn load(&mut self) -> Result<(), RustDroidError> {
        let path = Path::new(&self.file_path);
        if !path.exists() {
            // Write standard starter database
            self.db = PolicyDatabase::default();
            self.save()?;
            return Ok(());
        }

        let mut file = File::open(path).map_err(|e| RustDroidError::Io(e.to_string()))?;
        let mut content = String::new();
        file.read_to_string(&mut content)
            .map_err(|e| RustDroidError::Io(e.to_string()))?;

        self.db = serde_json::from_str(&content)
            .map_err(|e| RustDroidError::Serialization(e.to_string()))?;

        // Migration logic: If DB loaded has version < 2, upgrade to v2 cleanly!
        if self.db.migration_version < 2 {
            self.db.migration_version = 2;
            // Existing rules receive execution_policy: None (safe default)
            self.save()?;
        }

        // Purge UntilReboot rules upon load (since daemon startup/reload implies reboot/restart)
        let original_len = self.db.rules.len();
        self.db.rules.retain(|_, rule| rule.rule_type != PolicyRuleType::UntilReboot);
        if self.db.rules.len() != original_len {
            let _ = self.save();
        }

        Ok(())
    }

    /// Saves the database using safe atomic renaming
    pub fn save(&self) -> Result<(), RustDroidError> {
        let path = Path::new(&self.file_path);
        if let Some(parent) = path.parent() {
            if !parent.exists() {
                create_dir_all(parent).map_err(|e| RustDroidError::Io(e.to_string()))?;
            }
        }

        let content = serde_json::to_string_pretty(&self.db)
            .map_err(|e| RustDroidError::Serialization(e.to_string()))?;

        // Atomic write in the same directory to avoid partition boundaries crossings
        let filename = path.file_name().and_then(|n| n.to_str()).unwrap_or("policy.json");
        let tmp_path = path.with_file_name(format!("{}.tmp", filename));
        {
            let mut tmp_file = File::create(&tmp_path).map_err(|e| RustDroidError::Io(e.to_string()))?;
            tmp_file.write_all(content.as_bytes()).map_err(|e| RustDroidError::Io(e.to_string()))?;
            tmp_file.flush().map_err(|e| RustDroidError::Io(e.to_string()))?;
        }

        std::fs::rename(&tmp_path, path).map_err(|e| RustDroidError::Io(e.to_string()))?;

        Ok(())
    }

    /// Evaluates permission statefully. If Once or Expired, modifies the DB.
    /// Concurrency Note: The caller must guard evaluate_and_consume behind a Mutex
    /// lock to ensure atomic read-then-write consistency across multiple threads.
    pub fn evaluate_and_consume(&mut self, uid: u32, package_name: &str) -> SuDecision {
        // Root is always allowed
        if uid == 0 {
            return SuDecision::Allow;
        }

        // Android UID 1000 may only be auto-allowed in explicit test/dry-run fixtures.
        if uid == 1000 {
            if self.allow_uid_1000 {
                return SuDecision::Allow;
            }
            // Otherwise, fall through to explicit stored policy matching!
        }

        let mut should_remove = false;
        let mut decision = SuDecision::Ask;

        if let Some(rule) = self.db.rules.get(&uid) {
            if rule.package_name == package_name {
                let now = std::time::SystemTime::now()
                    .duration_since(std::time::UNIX_EPOCH)
                    .map(|d| d.as_secs())
                    .unwrap_or(0);

                if let Some(expires_at) = rule.expires_at {
                    if now > expires_at {
                        should_remove = true;
                    } else {
                        decision = rule.state.clone();
                        if rule.rule_type == PolicyRuleType::Once {
                            should_remove = true;
                        }
                    }
                } else {
                    decision = rule.state.clone();
                    if rule.rule_type == PolicyRuleType::Once {
                        should_remove = true;
                    }
                }
            }
        }

        if should_remove {
            self.db.rules.remove(&uid);
            let _ = self.save();
        }

        decision
    }

    /// Update or append a rule to the DB (extended for v0.5 with ExecutionPolicy)
    pub fn set_rule(
        &mut self,
        uid: u32,
        package_name: &str,
        state: SuDecision,
        rule_type: PolicyRuleType,
        execution_policy: Option<ExecutionPolicy>,
    ) -> Result<(), RustDroidError> {
        let timestamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);

        let expires_at = match rule_type {
            PolicyRuleType::ForDuration { seconds } => Some(timestamp + seconds),
            _ => None,
        };

        let rule = PolicyEntry {
            uid,
            package_name: package_name.to_string(),
            state: state.clone(),
            rule_type,
            created_at: timestamp,
            expires_at,
            execution_policy,
        };

        self.db.rules.insert(uid, rule);
        self.save()?;

        let _ = log_event(AuditEvent::DaemonEvent {
            event: "PolicyUpdate".to_string(),
            details: format!("Set UID: {}, State: {:?}", uid, state),
        });

        Ok(())
    }

    /// Revoke rules for UID
    pub fn revoke_rule(&mut self, uid: u32) -> Result<(), RustDroidError> {
        if self.db.rules.remove(&uid).is_some() {
            self.save()?;
            let _ = log_event(AuditEvent::DaemonEvent {
                event: "PolicyRevoke".to_string(),
                details: format!("Revoked rule for UID: {}", uid),
            });
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::remove_file;

    #[test]
    fn test_policy_load_save() {
        let test_db_path = "./policy_test.json";
        let mut engine = PolicyEngine::with_path(test_db_path);
        
        // Root allowed automatically
        assert_eq!(engine.evaluate_and_consume(0, "root"), SuDecision::Allow);
        // UID 1000 is NOT allowed in real/production mode
        assert_eq!(engine.evaluate_and_consume(1000, "system"), SuDecision::Ask);

        // Auto-allow UID 1000 allowed in explicit test fixtures
        engine.allow_uid_1000 = true;
        assert_eq!(engine.evaluate_and_consume(1000, "system"), SuDecision::Allow);

        // Unknown packages default to Ask
        assert_eq!(engine.evaluate_and_consume(10000, "com.app.test"), SuDecision::Ask);

        engine.set_rule(10000, "com.app.test", SuDecision::Allow, PolicyRuleType::Always, None).unwrap();
        assert_eq!(engine.evaluate_and_consume(10000, "com.app.test"), SuDecision::Allow);

        // Cleanup
        let _ = remove_file(test_db_path);
    }

    #[test]
    fn test_policy_allow_decision() {
        let test_db_path = "./policy_test_allow.json";
        let mut engine = PolicyEngine::with_path(test_db_path);
        engine.set_rule(10001, "com.allow.pkg", SuDecision::Allow, PolicyRuleType::Always, None).unwrap();

        let decision = engine.evaluate_and_consume(10001, "com.allow.pkg");
        assert_eq!(decision, SuDecision::Allow);

        let _ = remove_file(test_db_path);
    }

    #[test]
    fn test_policy_deny_decision() {
        let test_db_path = "./policy_test_deny.json";
        let mut engine = PolicyEngine::with_path(test_db_path);
        engine.set_rule(10002, "com.deny.pkg", SuDecision::Deny, PolicyRuleType::Always, None).unwrap();

        let decision = engine.evaluate_and_consume(10002, "com.deny.pkg");
        assert_eq!(decision, SuDecision::Deny);

        let _ = remove_file(test_db_path);
    }

    #[test]
    fn test_policy_duration_and_once() {
        let test_db_path = "./policy_test_dur.json";
        let mut engine = PolicyEngine::with_path(test_db_path);

        // Allow once
        engine.set_rule(10005, "com.once", SuDecision::Allow, PolicyRuleType::Once, None).unwrap();
        // First call allows
        assert_eq!(engine.evaluate_and_consume(10005, "com.once"), SuDecision::Allow);
        // Second call defaults to Ask
        assert_eq!(engine.evaluate_and_consume(10005, "com.once"), SuDecision::Ask);

        // For duration expired
        engine.set_rule(10006, "com.duration", SuDecision::Allow, PolicyRuleType::ForDuration { seconds: 0 }, None).unwrap();
        // Sleep slightly to guarantee expiration
        std::thread::sleep(std::time::Duration::from_secs(1));
        assert_eq!(engine.evaluate_and_consume(10006, "com.duration"), SuDecision::Ask);

        let _ = remove_file(test_db_path);
    }

    #[test]
    fn test_policy_concurrency_safe_once() {
        use std::sync::{Arc, Mutex};
        use std::thread;
        
        let test_db_path = "./policy_concurrency_test.json";
        let _ = remove_file(test_db_path);

        let mut engine = PolicyEngine::with_path(test_db_path);
        engine.set_rule(
            10055,
            "com.concurrency.once",
            SuDecision::Allow,
            PolicyRuleType::Once,
            None,
        ).unwrap();

        let engine_arc = Arc::new(Mutex::new(engine));
        let mut handles = vec![];
        let allowed_count = Arc::new(Mutex::new(0));

        for _ in 0..10 {
            let engine_clone = Arc::clone(&engine_arc);
            let allowed_clone = Arc::clone(&allowed_count);
            let handle = thread::spawn(move || {
                let dec = {
                    // Lock the critical section: keeping it extremely small and well-documented
                    let mut eng = engine_clone.lock().unwrap();
                    eng.evaluate_and_consume(10055, "com.concurrency.once")
                };
                if dec == SuDecision::Allow {
                    let mut cnt = allowed_clone.lock().unwrap();
                    *cnt += 1;
                }
            });
            handles.push(handle);
        }

        for h in handles {
            h.join().unwrap();
        }

        // Only exactly ONE thread should be allowed!
        assert_eq!(*allowed_count.lock().unwrap(), 1);

        let _ = remove_file(test_db_path);
    }

    #[test]
    fn test_policy_migration_v1_to_v2() {
        let test_db_path = "./policy_migration_test.json";
        let _ = remove_file(test_db_path);

        // Create a mock raw JSON content representing version 1 schema (which has rules without execution_policy)
        {
            let mut file = File::create(test_db_path).unwrap();
            let raw_v1_json = r#"{
                "migration_version": 1,
                "rules": {
                    "10099": {
                        "uid": 10099,
                        "package_name": "com.v1.app",
                        "state": "Allow",
                        "rule_type": "Always",
                        "created_at": 100000,
                        "expires_at": null
                    }
                }
            }"#;
            file.write_all(raw_v1_json.as_bytes()).unwrap();
        }

        // Instantiating the engine triggers the migration to v2!
        let engine = PolicyEngine::with_path(test_db_path);
        
        assert_eq!(engine.db.migration_version, 2);
        let rule = engine.db.rules.get(&10099).unwrap();
        assert_eq!(rule.package_name, "com.v1.app");
        assert_eq!(rule.execution_policy, None); // Migrated safely to None

        let _ = remove_file(test_db_path);
    }
}
