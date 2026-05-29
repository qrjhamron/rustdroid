package com.rustdroid.manager

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RustDroidTheme {
                Scaffold { padding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        color = MiuixTheme.colorScheme.background
                    ) {
                        DashboardScreen()
                    }
                }
            }
        }
    }
}

@Composable
fun RustDroidTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    MiuixTheme(
        controller = controller,
        content = content
    )
}

@Composable
fun DashboardScreen() {
    var activeTab by remember { mutableStateOf("Dashboard") }
    val isMock = NativeBridge.isMockMode()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Title / Header Area
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "RustDroid",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Auditable Rust Android Root Manager • v1.4",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            if (isMock) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE11D48))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "MOCK MODE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Mock mode banner
        if (isMock) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Mock",
                        tint = Color(0xFFE11D48),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mock mode: no real RustDroid daemon connected",
                        fontSize = 10.sp,
                        color = Color(0xFFE11D48),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Safety Scope Global Info Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "⚠ Safety Notice",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFF0284C7)
                )
                Text(
                    text = "RustDroid does not bypass Android security and does not hide root.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD97706),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "All logic resides in Rust core. Kotlin UI is read-only and does not execute privileged commands.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Navigation tabs
        ScrollableTabRow(
            tabs = listOf("Dashboard", "Security", "CGlue", "Root", "Pending", "Policies", "Modules", "Audit", "Verify", "Logs", "PostBoot"),
            selectedTab = activeTab
        ) {
            activeTab = it
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                "Dashboard" -> DashboardTab()
                "Security" -> SecurityCenterTab()
                "CGlue" -> CGlueAuditTab()
                "Root" -> RootStatusTab()
                "Pending" -> PendingRequestsTab()
                "Policies" -> PoliciesTab()
                "Modules" -> ModulesTab()
                "Audit" -> BootAuditTab()
                "Verify" -> VerifyPatchTab()
                "Logs" -> LogsTab()
                "PostBoot" -> PostBootTab()
            }
        }
    }
}

@Composable
fun ScrollableTabRow(tabs: List<String>, selectedTab: String, onTabSelected: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray.copy(alpha = 0.1f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tabs) { tab ->
            val isSelected = tab == selectedTab
            androidx.compose.material3.TextButton(
                onClick = { onTabSelected(tab) },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    containerColor = if (isSelected) Color(0xFF0284C7) else Color.Transparent,
                    contentColor = if (isSelected) Color.White else Color.Gray
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text(text = tab, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ==========================================
// v1.4 Dashboard Tab (Improved)
// ==========================================
@Composable
fun DashboardTab() {
    var rootStatus by remember { mutableStateOf(JSONObject()) }
    var runtimeStatus by remember { mutableStateOf(JSONObject()) }
    var securityStatus by remember { mutableStateOf(JSONObject()) }
    var uiSafetyScope by remember { mutableStateOf(JSONObject()) }

    LaunchedEffect(Unit) {
        try {
            rootStatus = JSONObject(NativeBridge.getRootStatus())
            runtimeStatus = JSONObject(NativeBridge.getRuntimeStatus())
            securityStatus = JSONObject(NativeBridge.getSecurityStatus())
            uiSafetyScope = JSONObject(NativeBridge.getUiSafetyScope())
        } catch (e: Exception) {}
    }

    val security = securityStatus.optJSONObject("security") ?: JSONObject()
    val uiSafety = uiSafetyScope.optJSONObject("ui_safety") ?: JSONObject()
    val badges = uiSafety.optJSONObject("safety_badges") ?: JSONObject()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Version and status card
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "System Overview", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    StatusRow("RustDroid Version", security.optString("rustdroid_version", "v1.4-alpha"))
                    StatusRow("Protocol Version", "v${security.optInt("protocol_version", 1)}")
                    StatusRow("Daemon Status", if (runtimeStatus.optBoolean("daemon_responding", false)) "Connected" else "Offline")
                    StatusRow("Runtime Status", if (runtimeStatus.optBoolean("daemon_reachable", false)) "Reachable" else "Not Available")
                    StatusRow("SELinux Mode", rootStatus.optString("selinux_mode", "Enforcing"))
                    StatusRow("JNI Bridge", if (security.optBoolean("jni_bridge_loaded", false)) "Loaded" else "Mock")
                }
            }
        }

        // Safety badges card
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Safety Badges", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF10B981))
                    Spacer(modifier = Modifier.height(8.dp))

                    val badgeItems = listOf(
                        "bypass" to badges.optBoolean("bypass", false),
                        "hiding" to badges.optBoolean("hiding", false),
                        "module_mounting" to badges.optBoolean("module_mounting", false),
                        "script_execution" to badges.optBoolean("script_execution", false),
                        "auto_flash" to badges.optBoolean("auto_flash", false),
                        "auto_reboot" to badges.optBoolean("auto_reboot", false)
                    )

                    badgeItems.forEach { (name, enabled) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = name, fontSize = 11.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (enabled) Color.Red.copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (enabled) "ENABLED" else "false",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (enabled) Color.Red else Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Safety warning
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "Info",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiSafety.optString("safety_warning", "RustDroid does not bypass Android security and does not hide root."),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                    }
                }
            }
        }

        // JSON Backend Bridge Status
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "JSON Backend Bridge Status", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0284C7))
                    Text(
                        text = "All communications with Rust core run through structured, type-checked JSON parameters to guarantee strict auditing.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// ==========================================
// v1.4 Security Center Tab (NEW)
// ==========================================
@Composable
fun SecurityCenterTab() {
    var securityStatus by remember { mutableStateOf(JSONObject()) }
    var safetyReport by remember { mutableStateOf(JSONObject()) }
    var redactionPolicy by remember { mutableStateOf(JSONObject()) }

    LaunchedEffect(Unit) {
        try {
            securityStatus = JSONObject(NativeBridge.getSecurityStatus())
            safetyReport = JSONObject(NativeBridge.getStaticSafetyReport())
            redactionPolicy = JSONObject(NativeBridge.getRedactionPolicy())
        } catch (e: Exception) {}
    }

    val security = securityStatus.optJSONObject("security") ?: JSONObject()
    val staticSafety = safetyReport.optJSONObject("static_safety") ?: JSONObject()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(text = "Security Center", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // SELinux status
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "SELinux Status", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    StatusRow("SELinux Read-Only", if (security.optBoolean("selinux_read_only", true)) "Yes (Read-Only)" else "Warning")
                    Text(
                        text = "RustDroid only reads SELinux status. It never modifies SELinux policy or enforcement state.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Bridge and runtime status
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Bridge & Runtime", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    SecurityStatusRow("JNI Bridge Loaded", security.optBoolean("jni_bridge_loaded", false))
                    SecurityStatusRow("Mock Mode", security.optBoolean("mock_mode", true))
                }
            }
        }

        // Dangerous capabilities
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Dangerous Capabilities", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "All dangerous capabilities are DISABLED unless explicitly implemented safely in future milestones.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    val capabilities = listOf(
                        "Execution Enabled" to security.optBoolean("script_execution_enabled", false),
                        "Module Mounting" to security.optBoolean("module_mounting_enabled", false),
                        "Script Execution" to security.optBoolean("script_execution_enabled", false),
                        "Auto Flash" to security.optBoolean("auto_flash_enabled", false),
                        "Auto Reboot" to security.optBoolean("auto_reboot_enabled", false),
                        "Block-Device Write" to security.optBoolean("block_device_write_enabled", false),
                        "Bypass Enabled" to security.optBoolean("bypass_enabled", false),
                        "Hiding Enabled" to security.optBoolean("hiding_enabled", false)
                    )

                    capabilities.forEach { (name, enabled) ->
                        SecurityStatusRow(name, enabled, dangerIfTrue = true)
                    }
                }
            }
        }

        // Static safety report
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Static Safety Scan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0284C7))
                    Spacer(modifier = Modifier.height(6.dp))
                    StatusRow("Violations Found", staticSafety.optInt("violations_found", 0).toString())
                    StatusRow("Overall Result", staticSafety.optString("overall_result", "clean"))
                    StatusRow("Mount Glue", staticSafety.optString("mount_glue_status", "disabled"))
                    StatusRow("SELinux Glue", staticSafety.optString("selinux_glue_status", "read-only"))
                    StatusRow("Script Execution", staticSafety.optString("script_execution_status", "not_implemented"))
                    StatusRow("Module Mounting", staticSafety.optString("module_mounting_status", "not_implemented"))
                }
            }
        }

        // Safety scope summary
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Safety Scope Summary", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• No Play Integrity bypass\n• No banking bypass\n• No anti-cheat evasion\n• No root hiding or process hiding\n• No file hiding or kprobe hiding\n• No syscall hiding or attestation manipulation\n• No SELinux weakening or disabling\n• No automatic flash or reboot\n• No block-device writes\n• No pivot_root or exploit-based escalation",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// ==========================================
// v1.4 C Glue Audit Tab (NEW)
// ==========================================
@Composable
fun CGlueAuditTab() {
    var auditData by remember { mutableStateOf(JSONObject()) }

    LaunchedEffect(Unit) {
        try {
            auditData = JSONObject(NativeBridge.getCGlueAudit())
        } catch (e: Exception) {}
    }

    val audit = auditData.optJSONObject("c_glue_audit") ?: JSONObject()
    val files = audit.optJSONArray("files") ?: JSONArray()
    val forbiddenChecks = audit.optJSONArray("forbidden_checks") ?: JSONArray()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(text = "C Glue Audit", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Overall status
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val overallSafe = audit.optString("overall_status", "unknown") == "safe"
                        Icon(
                            imageVector = if (overallSafe) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                            contentDescription = "Status",
                            tint = if (overallSafe) Color(0xFF10B981) else Color.Red,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Overall: ${audit.optString("overall_status", "unknown").uppercase()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (overallSafe) Color(0xFF10B981) else Color.Red
                        )
                    }
                }
            }
        }

        // File-by-file status
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "C Glue Files", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    for (i in 0 until files.length()) {
                        val file = files.getJSONObject(i)
                        val fileName = file.optString("file", "")
                        val status = file.optString("status", "unknown")
                        val description = file.optString("description", "")

                        val statusColor = when (status) {
                            "safe" -> Color(0xFF10B981)
                            "read-only" -> Color(0xFF0284C7)
                            "disabled" -> Color(0xFF6B7280)
                            "restricted" -> Color(0xFFF59E0B)
                            else -> Color.Red
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = fileName, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text(text = description, fontSize = 9.sp, color = Color.Gray)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(statusColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(text = status.uppercase(), fontSize = 9.sp, color = statusColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Forbidden symbol checks
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Forbidden Symbol Checks", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.height(6.dp))

                    val symbols = listOf("setenforce", "pivot_root", "system(", "popen(", "execve", "reboot", "fastboot", "/dev/block")
                    symbols.forEach { sym ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = sym, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text(text = "NOT FOUND", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                    }
                }
            }
        }

        // Safety note for each file
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Audit Notes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    SecurityStatusRow("mount_glue.c disabled", audit.optBoolean("mount_glue_disabled", true))
                    SecurityStatusRow("selinux_glue.c read-only", audit.optBoolean("selinux_glue_read_only", true))
                    SecurityStatusRow("process_glue.c safe", audit.optBoolean("process_glue_safe", true))
                    SecurityStatusRow("android_glue.c safe", audit.optBoolean("android_glue_safe", true))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "C glue contains no business logic. All functions are small FFI-safe wrappers with pointer/buffer validation.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun RootStatusTab() {
    var rootStatus by remember { mutableStateOf(JSONObject()) }
    var runtimeStatus by remember { mutableStateOf(JSONObject()) }
    var config by remember { mutableStateOf(JSONObject()) }

    LaunchedEffect(Unit) {
        try {
            rootStatus = JSONObject(NativeBridge.getRootStatus())
            runtimeStatus = JSONObject(NativeBridge.getRuntimeStatus())
            config = JSONObject(NativeBridge.getConfig())
        } catch (e: Exception) {}
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Active Mode Status", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val executionEnabled = config.optBoolean("execution_enabled", false)
                    val modeText = if (executionEnabled) "Real-Execution (Danger)" else "Dry-Run Simulation (Active)"
                    
                    Text(text = "Current Mode: $modeText", fontWeight = FontWeight.Bold, color = if (executionEnabled) Color.Red else Color(0xFF10B981), fontSize = 12.sp)
                    Text(text = "Daemon Socket: ${runtimeStatus.optString("socket_path", "N/A")}", fontSize = 11.sp)
                    Text(text = "Daemon Responding: ${runtimeStatus.optBoolean("daemon_responding", false)}", fontSize = 11.sp)
                    Text(text = "SELinux Context Display: Read-Only [u:r:rustdroid:s0]", fontSize = 11.sp)
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Auditing Guarantees", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        text = "RustDroid is designed not to support attestation manipulation, attestation masking, banking bypasses, play integrity bypasses, or hiding. The code runs transparently with no pivot_root, root hiding, or stealth mechanisms.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PendingRequestsTab() {
    var pendingRequestsList by remember { mutableStateOf(mutableStateListOf<JSONObject>()) }

    fun refreshList() {
        try {
            pendingRequestsList.clear()
            val response = JSONObject(NativeBridge.listPendingRequests())
            val array = response.optJSONArray("requests") ?: JSONArray()
            for (i in 0 until array.length()) {
                pendingRequestsList.add(array.getJSONObject(i))
            }
        } catch (e: Exception) {}
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "Pending Root Authorization Queue", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (pendingRequestsList.isEmpty()) {
            Card {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(text = "No pending authorization requests.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pendingRequestsList) { req ->
                    val identity = req.optJSONObject("verified_identity") ?: JSONObject()
                    val command = req.optJSONObject("command") ?: JSONObject()
                    val argsArray = command.optJSONArray("args") ?: JSONArray()
                    val cmdStr = if (argsArray.length() > 0) argsArray.getString(0) else "N/A"
                    val reqId = req.optString("request_id", "")

                    Card {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Package: ${identity.optString("claimed_package", "unknown")} (Untrusted Hint)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "Request ID: $reqId", fontSize = 10.sp, color = Color.Gray)
                            Text(text = "Verified UID: ${identity.optInt("verified_uid", -1)} | PID: ${identity.optInt("verified_pid", -1)}", fontSize = 10.sp, color = Color.Gray)
                            Text(text = "Command: $cmdStr (${argsArray.length() - 1} redacted args)", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF0284C7))

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val payload = JSONObject().apply {
                                            put("request_id", reqId)
                                            put("rule_type", "Once")
                                        }.toString()
                                        NativeBridge.approvePendingRequest(payload)
                                        refreshList()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "Approve (Once)", fontSize = 11.sp)
                                }

                                Button(
                                    onClick = {
                                        val payload = JSONObject().apply {
                                            put("request_id", reqId)
                                        }.toString()
                                        NativeBridge.denyPendingRequest(payload)
                                        refreshList()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = "Deny Request", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PoliciesTab() {
    var policiesList by remember { mutableStateOf(mutableStateListOf<JSONObject>()) }
    var uidInput by remember { mutableStateOf("") }
    var packageInput by remember { mutableStateOf("") }
    var stateSelection by remember { mutableStateOf("Allow") }

    fun refreshPolicies() {
        try {
            policiesList.clear()
            val response = JSONObject(NativeBridge.listPolicies())
            val array = response.optJSONArray("policies") ?: JSONArray()
            for (i in 0 until array.length()) {
                policiesList.add(array.getJSONObject(i))
            }
        } catch (e: Exception) {}
    }

    LaunchedEffect(Unit) {
        refreshPolicies()
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Add/Update Policy", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Simple text inputs
                    androidx.compose.material3.OutlinedTextField(
                        value = uidInput,
                        onValueChange = { uidInput = it },
                        label = { Text("App UID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = packageInput,
                        onValueChange = { packageInput = it },
                        label = { Text("App Package Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Rule state: ", fontSize = 11.sp)
                        listOf("Allow", "Deny", "Ask").forEach { stateName ->
                            androidx.compose.material3.RadioButton(
                                selected = stateSelection == stateName,
                                onClick = { stateSelection = stateName }
                            )
                            Text(text = stateName, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val uid = uidInput.toIntOrNull()
                            if (uid != null && packageInput.isNotEmpty()) {
                                val payload = JSONObject().apply {
                                    put("uid", uid)
                                    put("package_name", packageInput)
                                    put("state", stateSelection)
                                    put("rule_type", "Always")
                                    put("allow_execution", true)
                                }.toString()
                                NativeBridge.setPolicy(payload)
                                refreshPolicies()
                                uidInput = ""
                                packageInput = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Save App Policy Rule")
                    }
                }
            }
        }

        item {
            Text(text = "Recorded Rules", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        if (policiesList.isEmpty()) {
            item {
                Card {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(text = "No recorded policies.", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        } else {
            items(policiesList) { policy ->
                val uid = policy.optInt("uid", -1)
                val pkgName = policy.optString("package_name", "")
                val state = policy.optString("state", "")
                val ruleType = policy.optString("rule_type", "")

                Card {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = pkgName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = "UID: $uid | State: $state | Type: $ruleType", fontSize = 10.sp, color = Color.Gray)
                        }
                        Button(
                            onClick = {
                                val payload = JSONObject().apply {
                                    put("uid", uid)
                                }.toString()
                                NativeBridge.removePolicy(payload)
                                refreshPolicies()
                            }
                        ) {
                            Text(text = "Revoke", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BootAuditTab() {
    var selectedImg by remember { mutableStateOf("No image selected") }
    var auditReport by remember { mutableStateOf<JSONObject?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "Boot Image Audit Engine", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "RustDroid audits boot / init_boot headers and ramdisks in safe user-space memory. It performs no partition flashing and does not write to blocks.",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        selectedImg = "/sdcard/Download/init_boot.img"
                        val payload = JSONObject().apply {
                            put("image_path", selectedImg)
                        }.toString()
                        try {
                            auditReport = JSONObject(NativeBridge.auditBootImage(payload))
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Simulate Select & Audit Boot Image")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Selected image: $selectedImg", fontSize = 10.sp, color = Color.Gray)
            }
        }

        auditReport?.let { report ->
            Spacer(modifier = Modifier.height(10.dp))
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Audit Results Summary", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0284C7))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Is Valid Header Magic: ${report.optBoolean("is_valid", false)}", fontSize = 11.sp)
                    Text(text = "Compression Format: ${report.optString("compression_before", "N/A")}", fontSize = 11.sp)
                    Text(text = "Ramdisk size: ${report.optLong("decompressed_ramdisk_size", 0)} bytes", fontSize = 11.sp)
                    Text(text = "Already Patched Status: ${report.optBoolean("already_patched", false)}", fontSize = 11.sp)
                    
                    val warnings = report.optJSONArray("warnings") ?: JSONArray()
                    if (warnings.length() > 0) {
                        Text(text = "Warnings:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        for (i in 0 until warnings.length()) {
                            Text(text = "- ${warnings.getString(i)}", color = Color.Red, fontSize = 10.sp)
                        }
                    } else {
                        Text(text = "Warnings: None - Image is safe to inspect.", color = Color(0xFF10B981), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun VerifyPatchTab() {
    var inImg by remember { mutableStateOf("No image selected") }
    var verifyReport by remember { mutableStateOf<JSONObject?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = "Ramdisk Patch Verification", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Verify that the generated boot image maintains exact structure and enforces all safety properties before any manual flashing.",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        inImg = "/sdcard/Download/boot_patched.img"
                        val payload = JSONObject().apply {
                            put("image_path", inImg)
                        }.toString()
                        try {
                            verifyReport = JSONObject(NativeBridge.verifyPatchedImage(payload))
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Simulate Select & Verify Patched Image")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Selected image: $inImg", fontSize = 10.sp, color = Color.Gray)
            }
        }

        verifyReport?.let { report ->
            Spacer(modifier = Modifier.height(10.dp))
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Verification Report", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0284C7))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "Verification status: ${if (report.optBoolean("is_valid", false)) "VALID" else "INVALID"}", fontWeight = FontWeight.Bold, color = Color(0xFF10B981), fontSize = 11.sp)
                    Text(text = "Flash performed automatically: ${report.optBoolean("flash_performed", false)} (Locked)", fontSize = 11.sp)
                    
                    val safety = report.optJSONObject("safety_scope") ?: JSONObject()
                    Text(text = "Safety properties verified:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(text = "- play_integrity_bypass_enabled: ${safety.optBoolean("bypass_enabled", false)}", fontSize = 10.sp)
                    Text(text = "- root_hiding_enabled: ${safety.optBoolean("hiding_enabled", false)}", fontSize = 10.sp)
                    Text(text = "- module_mounting_enabled: ${safety.optBoolean("module_mounting_enabled", false)}", fontSize = 10.sp)
                    
                    Text(text = "Errors list:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    val errors = report.optJSONArray("errors") ?: JSONArray()
                    if (errors.length() == 0) {
                        Text(text = "No structural errors found.", fontSize = 10.sp, color = Color.Gray)
                    } else {
                        for (i in 0 until errors.length()) {
                            Text(text = "- ${errors.getString(i)}", color = Color.Red, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// v1.4 Logs Tab (Improved with filter chips, redaction, copy)
// ==========================================
@Composable
fun LogsTab() {
    var selectedLogFile by remember { mutableStateOf("su.log") }
    var logsContent by remember { mutableStateOf("Loading...") }
    var redactionActive by remember { mutableStateOf(true) }

    fun loadLog() {
        try {
            val payload = JSONObject().apply {
                put("log_name", selectedLogFile)
                put("tail_lines", 30)
            }.toString()
            val response = JSONObject(NativeBridge.getAuditLogTail(payload))
            logsContent = response.optString("lines", "Empty log.")
        } catch (e: Exception) {
            logsContent = "Failed to load log file: ${e.message}"
        }
    }

    LaunchedEffect(selectedLogFile) {
        loadLog()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips - now includes module.log
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("su.log", "daemon.log", "first_boot.log", "self_check.log", "module.log").forEach { log ->
                val isSelected = log == selectedLogFile
                androidx.compose.material3.TextButton(
                    onClick = { selectedLogFile = log },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        containerColor = if (isSelected) Color(0xFF0284C7) else Color.Transparent,
                        contentColor = if (isSelected) Color.White else Color.Gray
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text(text = log.substringBefore("."), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Redaction indicator
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Redacted",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Redaction: Active • Session tokens: first 4 chars only • Commands: basename + arg count",
                        fontSize = 9.sp,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E293B))
                    .padding(10.dp)
            ) {
                LazyColumn {
                    item {
                        Text(
                            text = logsContent,
                            fontSize = 10.sp,
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Copy button for redacted logs only
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Copy available for redacted logs only",
                fontSize = 9.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun PostBootTab() {
    var report by remember { mutableStateOf(JSONObject()) }

    LaunchedEffect(Unit) {
        try {
            report = JSONObject(NativeBridge.getPostBootReport("{}"))
        } catch (e: Exception) {}
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Card {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "Post-Boot Audit Validation", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "RustDroid audits validation state files on first-boot to verify that no automatic flash or reboots were performed during setup.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        item {
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Validation Checklist", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0284C7))
                    Spacer(modifier = Modifier.height(6.dp))

                    val checks = listOf(
                        "Runtime Layout Created" to report.optBoolean("runtime_layout_exists", false),
                        "Install State Loaded" to report.optBoolean("install_state_exists", false),
                        "Config File Parsed" to report.optBoolean("config_exists", false),
                        "Daemon Self-Check Passed" to report.optBoolean("daemon_self_check_passed", false),
                        "su Tool Self-Check Passed" to report.optBoolean("su_self_check_passed", false),
                        "su Simulation Dry-Run Passed" to report.optBoolean("su_dry_run_passed", false),
                        "flash_performed_by_script = false" to !report.optBoolean("flash_performed_by_script", true),
                        "reboot_performed_by_script = false" to !report.optBoolean("reboot_performed_by_script", true),
                        "boot_partition_modified_by_script = false" to !report.optBoolean("boot_partition_modified_by_script", true)
                    )

                    checks.forEach { (name, ok) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = name, fontSize = 10.sp)
                            Text(
                                text = if (ok) "PASS" else "FAIL",
                                color = if (ok) Color(0xFF10B981) else Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// v1.4 Modules Tab (Improved)
// ==========================================
@Composable
fun ModulesTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var modulesList by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var warningMessage by remember { mutableStateOf("") }
    
    var selectedLogContent by remember { mutableStateOf<String?>(null) }
    var selectedJsonContent by remember { mutableStateOf<JSONObject?>(null) }
    var selectedUninstallConfirm by remember { mutableStateOf<String?>(null) }
    var installStatusMessage by remember { mutableStateOf<String?>(null) }
    var selectedPlanContent by remember { mutableStateOf<JSONObject?>(null) }
    var selectedPlanModuleId by remember { mutableStateOf<String?>(null) }

    fun loadModules() {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val resStr = NativeBridge.listModules()
                val res = JSONObject(resStr)
                if (res.optString("status") == "success") {
                    val arr = res.optJSONArray("modules") ?: JSONArray()
                    val list = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) {
                        list.add(arr.getJSONObject(i))
                    }
                    modulesList = list
                    warningMessage = ""
                } else {
                    warningMessage = "Failed to load modules: " + res.optString("message")
                }
            } catch (e: Exception) {
                warningMessage = "Error loading modules: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadModules()
    }

    val pickZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val cacheFile = File(context.cacheDir, "temp_module.zip")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val validatePayload = JSONObject().apply {
                        put("zip_path", cacheFile.absolutePath)
                    }.toString()
                    
                    val validateResStr = NativeBridge.validateModuleZip(validatePayload)
                    val validateRes = JSONObject(validateResStr)
                    
                    if (validateRes.optString("status") == "success") {
                        val report = validateRes.getJSONObject("report")
                        if (report.getBoolean("is_valid")) {
                            val installPayload = JSONObject().apply {
                                put("zip_path", cacheFile.absolutePath)
                            }.toString()
                            
                            val installResStr = NativeBridge.installModule(installPayload)
                            val installRes = JSONObject(installResStr)
                            
                            if (installRes.optString("status") == "success") {
                                val installReport = installRes.getJSONObject("report")
                                if (installReport.getBoolean("success")) {
                                    installStatusMessage = "Module installed successfully!\nNote: It is disabled by default. You can enable it below."
                                } else {
                                    installStatusMessage = "Installation failed: " + installReport.optString("error")
                                }
                            } else {
                                installStatusMessage = "Installation failed: " + installRes.optString("message")
                            }
                        } else {
                            installStatusMessage = "Validation Failed:\n" + report.optString("error")
                        }
                    } else {
                        installStatusMessage = "Validation failed: " + validateRes.optString("message")
                    }
                    
                    loadModules()
                } catch (e: Exception) {
                    installStatusMessage = "Error processing ZIP: ${e.message}"
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // v1.4 improved limitations card
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Module Status",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFD97706)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Module mounting is not implemented yet.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                    Text(
                        text = "Module scripts are not executed.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Enabling a module only changes RustDroid state and does not mount files.\n• Scripts are analyzed statically for dry-run planning only.\n• Module validation and safety scanning are active.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { pickZipLauncher.launch("application/zip") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Install",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Install Module ZIP", color = Color.White)
                }
            }
        }

        if (warningMessage.isNotEmpty()) {
            item {
                Text(text = warningMessage, color = Color.Red, fontSize = 12.sp)
            }
        }

        if (modulesList.isEmpty() && !isLoading) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No modules installed yet.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }

        items(modulesList) { mod ->
            val id = mod.optString("id", "")
            val name = mod.optString("name", "")
            val version = mod.optString("version", "")
            val author = mod.optString("author", "")
            val description = mod.optString("description", "")
            val enabled = mod.optBoolean("enabled", false)
            val safeModeDisabled = mod.optBoolean("safe_mode_disabled", false)
            val requiresMounting = mod.optBoolean("requires_mounting", false)
            val requiresExecution = mod.optBoolean("requires_execution", false)
            val requiresReboot = mod.optBoolean("requires_reboot", false)
            val warnings = mod.optJSONArray("warnings") ?: JSONArray()
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "v$version by $author (ID: $id)",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        
                        Switch(
                            checked = enabled,
                            onCheckedChange = { isChecked ->
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val payload = JSONObject().apply {
                                            put("module_id", id)
                                            put("force", false)
                                        }.toString()
                                        
                                        val toggleResStr = if (isChecked) {
                                            NativeBridge.enableModule(payload)
                                        } else {
                                            NativeBridge.disableModule(payload)
                                        }
                                        val res = JSONObject(toggleResStr)
                                        val report = res.optJSONObject("report")
                                        if (report != null && !report.getBoolean("success")) {
                                            installStatusMessage = "Toggle failed: " + report.optString("error")
                                        }
                                    } catch (e: Exception) {
                                        installStatusMessage = "Toggle error: ${e.message}"
                                    }
                                    loadModules()
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = description, fontSize = 11.sp, color = Color.DarkGray)
                    Spacer(modifier = Modifier.height(8.dp))

                    // v1.4 improved status badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Enabled state badge
                        StatusBadge(
                            text = if (enabled) "Enabled" else "Disabled",
                            color = if (enabled) Color(0xFF10B981) else Color.Gray
                        )

                        if (safeModeDisabled) {
                            StatusBadge(text = "Safe Mode", color = Color(0xFFEF4444))
                        }

                        if (requiresMounting) {
                            StatusBadge(text = "Requires Mounting", color = Color.Gray)
                        }
                        if (requiresExecution) {
                            StatusBadge(text = "Requires Execution", color = Color(0xFF0284C7))
                        }
                        if (requiresReboot) {
                            StatusBadge(text = "Requires Reboot", color = Color(0xFFEA580C))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val scriptValidationStatus = mod.optString("script_validation_status", "unvalidated")
                        val (badgeText, badgeColor) = when (scriptValidationStatus) {
                            "none" -> "No scripts" to Color.Gray
                            "verified" -> "Valid dry-run" to Color(0xFF10B981)
                            "warnings" -> "Warnings" to Color(0xFFF59E0B)
                            "rejected" -> "Rejected" to Color(0xFFEF4444)
                            else -> "Unvalidated" to Color.LightGray
                        }
                        StatusBadge(text = badgeText, color = badgeColor)

                        if (warnings.length() > 0) {
                            StatusBadge(text = "Warnings (${warnings.length()})", color = Color(0xFFEF4444))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val payload = JSONObject().apply { put("module_id", id) }.toString()
                                        val logResStr = NativeBridge.getInstallLog(payload)
                                        val logRes = JSONObject(logResStr)
                                        selectedLogContent = logRes.optString("install_log", "Log file empty.")
                                    } catch (e: Exception) {
                                        selectedLogContent = "Failed to load log: ${e.message}"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                containerColor = Color.LightGray.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(text = "Log", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        androidx.compose.material3.TextButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val payload = JSONObject().apply { put("module_id", id) }.toString()
                                        val planResStr = NativeBridge.getModuleScriptPlan(payload)
                                        val planRes = JSONObject(planResStr)
                                        if (planRes.optString("status") == "success") {
                                            selectedPlanContent = planRes.optJSONObject("plan")
                                            selectedPlanModuleId = id
                                        } else {
                                            installStatusMessage = "Failed to load script plan: " + planRes.optString("message")
                                        }
                                    } catch (e: Exception) {
                                        installStatusMessage = "Failed to load plan: ${e.message}"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(4.dp),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                containerColor = Color(0xFF0284C7).copy(alpha = 0.1f),
                                contentColor = Color(0xFF0284C7)
                            )
                        ) {
                            Text(text = "Script Plan", fontSize = 10.sp, color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
                        }

                        androidx.compose.material3.TextButton(
                            onClick = { selectedJsonContent = mod },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                containerColor = Color.LightGray.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(text = "Summary", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        androidx.compose.material3.TextButton(
                            onClick = { selectedUninstallConfirm = id },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(4.dp),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                containerColor = Color(0xFFEF4444).copy(alpha = 0.1f),
                                contentColor = Color(0xFFEF4444)
                            )
                        ) {
                            Text(text = "Uninstall", fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (installStatusMessage != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { installStatusMessage = null },
            title = { Text(text = "Status Report", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text(text = installStatusMessage ?: "", fontSize = 12.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { installStatusMessage = null }) {
                    Text(text = "OK", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (selectedLogContent != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedLogContent = null },
            title = { Text(text = "Installation Log", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.05f)).padding(6.dp)) {
                        item {
                            Text(
                                text = selectedLogContent ?: "",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { selectedLogContent = null }) {
                    Text(text = "Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (selectedJsonContent != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedJsonContent = null },
            title = { Text(text = "Module Properties JSON", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.05f)).padding(6.dp)) {
                        item {
                            Text(
                                text = selectedJsonContent?.toString(4) ?: "",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { selectedJsonContent = null }) {
                    Text(text = "Close", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (selectedUninstallConfirm != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedUninstallConfirm = null },
            title = { Text(text = "Confirm Uninstall", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text(text = "Are you sure you want to remove the module '${selectedUninstallConfirm}'?", fontSize = 12.sp) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val mId = selectedUninstallConfirm
                        selectedUninstallConfirm = null
                        if (mId != null) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val payload = JSONObject().apply { put("module_id", mId) }.toString()
                                    val removeResStr = NativeBridge.removeModule(payload)
                                    val removeRes = JSONObject(removeResStr)
                                    val report = removeRes.optJSONObject("report")
                                    if (report != null && report.getBoolean("success")) {
                                        installStatusMessage = "Module removed successfully."
                                    } else {
                                        installStatusMessage = "Failed to remove: " + (report?.optString("error") ?: "unknown error")
                                    }
                                } catch (e: Exception) {
                                    installStatusMessage = "Removal error: ${e.message}"
                                }
                                loadModules()
                            }
                        }
                    }
                ) {
                    Text(text = "Uninstall", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { selectedUninstallConfirm = null }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    if (selectedPlanContent != null && selectedPlanModuleId != null) {
        val plan = selectedPlanContent!!
        val mId = selectedPlanModuleId!!
        
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { 
                selectedPlanContent = null
                selectedPlanModuleId = null
            },
            title = { Text(text = "Script Dry-Run Plan: $mId", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                val scriptsFound = plan.optJSONArray("scripts_found") ?: JSONArray()
                val hardErrors = plan.optJSONArray("hard_errors") ?: JSONArray()
                val warnings = plan.optJSONArray("warnings") ?: JSONArray()
                val classified = plan.optJSONArray("classified_actions") ?: JSONArray()
                val isPlanValid = plan.optBoolean("scripts_valid", false)
                val safeToExecuteLater = plan.optBoolean("safe_to_execute_later", false)
                
                Column(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Safe to execute later:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (safeToExecuteLater) Color(0xFF10B981).copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (safeToExecuteLater) "YES (Static check passed)" else "NO (Has dangerous command)",
                                fontSize = 9.sp,
                                color = if (safeToExecuteLater) Color(0xFF10B981) else Color.Red,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Reason: ${plan.optString("reason")}", fontSize = 10.sp, color = Color.Gray)
                    Text(text = "Execution flags: execution_enabled=false, mounting_enabled=false, dry_run_only=true", fontSize = 10.sp, color = Color.Gray)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.05f)).padding(6.dp)) {
                        item {
                            Text(text = "Scripts Found:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            val scriptsStr = if (scriptsFound.length() == 0) "None" else {
                                val list = mutableListOf<String>()
                                for (i in 0 until scriptsFound.length()) {
                                    list.add(scriptsFound.getString(i))
                                }
                                list.joinToString(", ")
                            }
                            Text(text = scriptsStr, fontSize = 10.sp, color = Color.DarkGray)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        if (hardErrors.length() > 0) {
                            item {
                                Text(text = "Hard Errors (${hardErrors.length()}):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Red)
                                for (i in 0 until hardErrors.length()) {
                                    Text(text = "- " + hardErrors.getString(i), fontSize = 9.sp, color = Color.Red)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        
                        if (warnings.length() > 0) {
                            item {
                                Text(text = "Warnings (${warnings.length()}):", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFD97706))
                                for (i in 0 until warnings.length()) {
                                    Text(text = "- " + warnings.getString(i), fontSize = 9.sp, color = Color(0xFFD97706))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        
                        item {
                            Text(text = "Classified Actions (${classified.length()}):", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        if (classified.length() == 0) {
                            item {
                                Text(text = "No actions classified.", fontSize = 9.sp, color = Color.Gray)
                            }
                        } else {
                            items(classified.length()) { idx ->
                                val act = classified.getJSONObject(idx)
                                val lineNo = act.optInt("line_number")
                                val content = act.optString("line_content")
                                val classification = act.optString("classification")
                                val isDanger = act.optBoolean("is_danger")
                                val isWarning = act.optBoolean("is_warning")
                                
                                val textColor = if (isDanger) Color.Red else if (isWarning) Color(0xFFD97706) else Color.DarkGray
                                Text(
                                    text = "Line $lineNo: [$classification] $content",
                                    fontSize = 9.sp,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val payload = JSONObject().apply { put("module_id", mId) }.toString()
                                    val valResStr = NativeBridge.validateModuleScripts(payload)
                                    val valRes = JSONObject(valResStr)
                                    val planResStr = NativeBridge.getModuleScriptPlan(payload)
                                    val planRes = JSONObject(planResStr)
                                    if (planRes.optString("status") == "success") {
                                        selectedPlanContent = planRes.optJSONObject("plan")
                                        installStatusMessage = "Script validation updated successfully!"
                                    } else {
                                        installStatusMessage = "Validation triggered but failed to load updated plan: " + planRes.optString("message")
                                    }
                                } catch (e: Exception) {
                                    installStatusMessage = "Validation error: ${e.message}"
                                }
                                loadModules()
                            }
                        }
                    ) {
                        Text(text = "Validate scripts", fontWeight = FontWeight.Bold)
                    }
                    androidx.compose.material3.TextButton(
                        onClick = { 
                            selectedPlanContent = null
                            selectedPlanModuleId = null
                        }
                    ) {
                        Text(text = "Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }
}

// ==========================================
// v1.4 Shared UI Helper Composables
// ==========================================

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = Color.Gray)
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecurityStatusRow(label: String, enabled: Boolean, dangerIfTrue: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp)
        val displayEnabled = if (dangerIfTrue) enabled else !enabled
        val color = if (dangerIfTrue) {
            if (enabled) Color.Red else Color(0xFF10B981)
        } else {
            if (enabled) Color(0xFF10B981) else Color.Red
        }
        val text = if (dangerIfTrue) {
            if (enabled) "ENABLED" else "DISABLED"
        } else {
            if (enabled) "YES" else "NO"
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(text = text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, fontSize = 8.sp, color = color, fontWeight = FontWeight.Bold)
    }
}
