package com.familyguard.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.familyguard.app.viewmodel.AuthViewModel

/** Shows the logout progress: a child device waits here for a parent's approval. */
@Composable
fun LogoutApprovalDialog(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val message = uiState.logoutMessage ?: return

    AlertDialog(
        onDismissRequest = viewModel::dismissLogout,
        title = { Text("Выход из аккаунта") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.logoutPending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::dismissLogout) {
                Text(if (uiState.logoutPending) "Отмена" else "Закрыть")
            }
        }
    )
}
