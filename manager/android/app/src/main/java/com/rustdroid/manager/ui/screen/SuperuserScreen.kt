package com.rustdroid.manager.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rustdroid.manager.ui.SuperuserEntry
import com.rustdroid.manager.ui.SuperuserUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun SuperuserScreen(
    state: SuperuserUiState,
    onRefresh: () -> Unit,
    onRevoke: (uid: Int) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
        }
        return
    }

    // Error state
    state.errorMessage?.let { error ->
        EmptyState(
            icon = Icons.Rounded.AdminPanelSettings,
            title = "Superuser unavailable",
            subtitle = error
        )
        return
    }

    // Empty state
    if (state.entries.isEmpty()) {
        EmptyState(
            icon = Icons.Rounded.AdminPanelSettings,
            title = "No superuser requests yet",
            subtitle = "Apps that request root access will appear here."
        )
        return
    }

    // Policy list
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = "Superuser Policies",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        items(state.entries) { entry ->
            PolicyItem(entry = entry, onRevoke = { onRevoke(entry.uid) })
        }
    }
}

@Composable
private fun PolicyItem(entry: SuperuserEntry, onRevoke: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.packageName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = "UID ${entry.uid} · ${entry.state} · ${entry.ruleType}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            val stateColor = when (entry.state.lowercase()) {
                "allow" -> Color(0xFF10B981)
                "deny" -> Color(0xFFEF4444)
                else -> Color(0xFFF59E0B)
            }
            Text(
                text = entry.state,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = stateColor,
                modifier = Modifier.padding(end = 8.dp)
            )
            IconButton(onClick = onRevoke, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Revoke",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
