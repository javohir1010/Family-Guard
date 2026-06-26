package com.familyguard.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familyguard.app.ui.screens.*
import com.familyguard.app.ui.theme.FamilyGuardTheme
import com.familyguard.app.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FamilyGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val uiState by authViewModel.uiState.collectAsState()
                    val isLoggedIn by authViewModel.isLoggedIn.collectAsState(initial = false)

                    // Determine start destination
                    val startDest = when {
                        !isLoggedIn -> "login"
                        !uiState.hasFamily -> "onboarding"
                        else -> "sos"
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDest
                    ) {
                        composable("login") {
                            LoginScreen(
                                viewModel = authViewModel,
                                onNavigateToRegister = { navController.navigate("register") },
                                onLoginSuccess = {
                                    val target = if (uiState.hasFamily) "sos" else "onboarding"
                                    navController.navigate(target) {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("register") {
                            RegisterScreen(
                                viewModel = authViewModel,
                                onNavigateToLogin = { navController.popBackStack() },
                                onRegisterSuccess = {
                                    navController.navigate("onboarding") {
                                        popUpTo("register") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("onboarding") {
                            OnboardingScreen(
                                onJoinSuccess = {
                                    navController.navigate("sos") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("sos") {
                            SosScreen()
                        }
                    }
                }
            }
        }
    }
}
