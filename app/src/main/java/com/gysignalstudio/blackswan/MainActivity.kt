package com.gysignalstudio.blackswan

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gysignalstudio.blackswan.ads.AdMobManager
import com.gysignalstudio.blackswan.billing.BillingManager
import com.gysignalstudio.blackswan.engagement.EngagementPromptManager
import com.gysignalstudio.blackswan.engagement.InAppReviewManager
import com.gysignalstudio.blackswan.ui.screens.*
import com.gysignalstudio.blackswan.ui.theme.MyApplicationTheme
import com.gysignalstudio.blackswan.ui.viewmodel.OsintViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: OsintViewModel = viewModel()
            val themeSelection by viewModel.themeSelection.collectAsState()
            val language by viewModel.selectedLanguage.collectAsState()
            val adsDisabled by viewModel.adsDisabled.collectAsState()
            val billingMessage by BillingManager.message.collectAsState()
            val showAdFreePrompt by EngagementPromptManager.showAdFreePrompt.collectAsState()
            val showRateAppPrompt by EngagementPromptManager.showRateAppPrompt.collectAsState()
            val isDarkTheme = themeSelection != "light"
            val isUk = language == "uk"

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !isDarkTheme
                    isAppearanceLightNavigationBars = !isDarkTheme
                }
            }

            LaunchedEffect(Unit) {
                BillingManager.connect(this@MainActivity)
            }

            LaunchedEffect(adsDisabled) {
                AdMobManager.setAdsDisabled(adsDisabled)
            }

            LaunchedEffect(billingMessage) {
                val message = billingMessage ?: return@LaunchedEffect
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                BillingManager.consumeMessage()
            }

            MyApplicationTheme(
                darkTheme = isDarkTheme,
                dynamicColor = false
            ) {
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
                                AdMobManager.requestConsentAndInitialize(this@MainActivity)
                                navController.navigate("main_hub") {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }

                    // Main App Navigation Hub (contains Bottom Nav bar, page 7)
                    composable("main_hub") {
                        LaunchedEffect(Unit) {
                            EngagementPromptManager.registerSession(applicationContext)
                        }
                        MainHubScaffold(
                            viewModel = viewModel,
                            onNavigateToEventDetails = {
                                navController.navigate("event_details")
                            },
                            onNavigateToSourceDetails = {
                                navController.navigate("source_details")
                            },
                            onPurchaseRemoveAds = {
                                BillingManager.launchRemoveAdsPurchase(this@MainActivity)
                            },
                            onRestorePurchases = {
                                BillingManager.restorePurchases()
                            },
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

                if (showAdFreePrompt) {
                    AdFreeOfferDialog(
                        isUk = isUk,
                        onDismiss = { EngagementPromptManager.dismissAdFreePrompt() },
                        onRemoveAds = {
                            EngagementPromptManager.dismissAdFreePrompt()
                            BillingManager.launchRemoveAdsPurchase(this@MainActivity)
                        }
                    )
                }

                if (showRateAppPrompt) {
                    RateAppDialog(
                        isUk = isUk,
                        onDismiss = { EngagementPromptManager.dismissRateAppPrompt() },
                        onRateApp = {
                            InAppReviewManager.launch(this@MainActivity) { success ->
                                if (success) {
                                    EngagementPromptManager.markRateAppCompleted(applicationContext)
                                } else {
                                    EngagementPromptManager.dismissRateAppPrompt()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainHubScaffold(
    viewModel: OsintViewModel,
    onNavigateToEventDetails: () -> Unit,
    onNavigateToSourceDetails: () -> Unit,
    onPurchaseRemoveAds: () -> Unit,
    onRestorePurchases: () -> Unit,
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
            labelEn = "Total",
            labelUk = "Разом",
            icon = Icons.Default.BarChart
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
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp,
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
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = if (isUk) item.labelUk else item.labelEn,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                0 -> MapScreen(
                    viewModel = viewModel,
                    onNavigateToEventDetails = onNavigateToEventDetails
                )
                1 -> TotalScreen(viewModel = viewModel)
                2 -> ProfileScreen(
                    viewModel = viewModel,
                    onPurchaseRemoveAds = onPurchaseRemoveAds,
                    onRestorePurchases = onRestorePurchases,
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

@Composable
private fun AdFreeOfferDialog(
    isUk: Boolean,
    onDismiss: () -> Unit,
    onRemoveAds: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isUk) "Вимкніть рекламу одноразовою покупкою за $1" else "Go ad-free for a one-time purchase of \$1",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = if (isUk) {
                    "Підтримайте проєкт і продовжуйте користуватися Black Swan без реклами."
                } else {
                    "Support the project and continue using Black Swan without ads."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onRemoveAds) {
                Text(if (isUk) "Прибрати рекламу" else "Remove Ads")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isUk) "Можливо пізніше" else "Maybe later")
            }
        },
    )
}

@Composable
private fun RateAppDialog(
    isUk: Boolean,
    onDismiss: () -> Unit,
    onRateApp: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isUk) "Оцініть Black Swan у Google Play" else "Rate Black Swan on Google Play",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = if (isUk) {
                    "Якщо вам зручно, можете залишити оцінку в Google Play за допомогою вбудованого вікна."
                } else {
                    "If you'd like, you can leave a rating in Google Play using the in-app review flow."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onRateApp) {
                Text(if (isUk) "Оцінити застосунок" else "Rate app")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isUk) "Можливо пізніше" else "Maybe later")
            }
        },
    )
}
