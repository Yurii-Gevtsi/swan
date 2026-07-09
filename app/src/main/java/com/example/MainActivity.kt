package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.OsintViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(
                darkTheme = true, // Force Dark theme to fit 'The Black Swan' visual profile
                dynamicColor = false
            ) {
                val viewModel: OsintViewModel = viewModel()
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "splash",
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Splash & Onboarding Disclaimer Screen
                    composable("splash") {
                        SplashScreen(
                            viewModel = viewModel,
                            onOnboardingComplete = {
                                navController.navigate("main_hub") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }

                    // Main App Navigation Hub (contains Bottom Nav bar, page 7)
                    composable("main_hub") {
                        MainHubScaffold(
                            viewModel = viewModel,
                            onNavigateToEventDetails = {
                                navController.navigate("event_details")
                            },
                            onNavigateToSourceDetails = {
                                navController.navigate("source_details")
                            }
                        )
                    }

                    // Event Details Screen (page 10)
                    composable("event_details") {
                        EventDetailsScreen(
                            viewModel = viewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onNavigateToSource = {
                                navController.navigate("source_details")
                            }
                        )
                    }

                    // Source Details Screen (page 13)
                    composable("source_details") {
                        SourcesScreen(
                            viewModel = viewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MainHubScaffold(
    viewModel: OsintViewModel,
    onNavigateToEventDetails: () -> Unit,
    onNavigateToSourceDetails: () -> Unit
) {
    val language by viewModel.selectedLanguage.collectAsState()
    val isUk = language == "uk"

    var activeTab by remember { mutableStateOf(0) }

    val navItems = listOf(
        NavigationItemData(
            labelEn = "Map",
            labelUk = "Карта",
            icon = Icons.Default.Map
        ),
        NavigationItemData(
            labelEn = "Timeline",
            labelUk = "Реєстр",
            icon = Icons.Default.FormatListBulleted
        ),
        NavigationItemData(
            labelEn = "Areas",
            labelUk = "Регіони",
            icon = Icons.Default.Place
        ),
        NavigationItemData(
            labelEn = "Analyst",
            labelUk = "Аналітик",
            icon = Icons.Default.Hub
        ),
        NavigationItemData(
            labelEn = "Settings",
            labelUk = "Профіль",
            icon = Icons.Default.Settings
        )
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars
            ) {
                navItems.forEachIndexed { index, item ->
                    val selected = activeTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { activeTab = index },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = if (isUk) item.labelUk else item.labelEn,
                                tint = if (selected) Color(0xFF020617) else Color(0xFF94A3B8)
                            )
                        },
                        label = {
                            Text(
                                text = if (isUk) item.labelUk else item.labelEn,
                                color = if (selected) Color.White else Color(0xFF94A3B8)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF020617),
                            indicatorColor = Color.White
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFF090D16)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> MapScreen(
                    viewModel = viewModel,
                    onNavigateToEventDetails = onNavigateToEventDetails,
                    onNavigateToRegionDetails = { activeTab = 2 }, // Navigate to Areas/Regions tab
                    onNavigateToMaritimeDetails = { activeTab = 2 }
                )
                1 -> TimelineScreen(
                    viewModel = viewModel,
                    onNavigateToEventDetails = onNavigateToEventDetails
                )
                2 -> AreasScreen(
                    viewModel = viewModel,
                    onNavigateToEventDetails = onNavigateToEventDetails
                )
                3 -> AnalystScreen(
                    viewModel = viewModel
                )
                4 -> ProfileScreen(
                    viewModel = viewModel
                )
            }
        }
    }
}

data class NavigationItemData(
    val labelEn: String,
    val labelUk: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
