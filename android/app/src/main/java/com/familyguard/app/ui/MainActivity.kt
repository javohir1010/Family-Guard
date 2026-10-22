package com.familyguard.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.familyguard.app.ui.screens.*
import com.familyguard.app.ui.theme.FamilyGuardTheme
import com.familyguard.app.ui.theme.ThemePrefs
import com.familyguard.app.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Grows with every tap on the SOS home-screen widget. */
    private val sosRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemePrefs.init(this)
        sosRequests.intValue = savedInstanceState?.getInt(KEY_SOS_REQUESTS) ?: 0
        // A recreated activity still holds the widget intent: count it only once
        if (savedInstanceState == null) handleSosIntent(intent)
        setContent {
            FamilyGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val uiState by authViewModel.uiState.collectAsState()
                    val isLoggedIn by authViewModel.isLoggedIn.collectAsState(initial = null)

                    // Wait for the stored session, otherwise the graph is built with
                    // "login" first and rebuilt (login → onboarding → sos flashes)
                    if (isLoggedIn == null || !uiState.isReady) return@Surface

                    // Determine start destination
                    val startDest = when {
                        isLoggedIn != true -> "login"
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
                            HomeScreen(sosRequests = sosRequests.intValue)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSosIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SOS_REQUESTS, sosRequests.intValue)
    }

    private fun handleSosIntent(intent: Intent?) {
        if (intent?.action == ACTION_SOS) sosRequests.intValue++
    }

    companion object {
        const val ACTION_SOS = "com.familyguard.app.action.SOS"
        private const val KEY_SOS_REQUESTS = "sos_requests"
    }
}
