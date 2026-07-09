package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.OsintViewModel

@Composable
fun SplashScreen(
    viewModel: OsintViewModel,
    onOnboardingComplete: () -> Unit
) {
    val acceptedDisclaimer by viewModel.acceptedDisclaimer.collectAsState()
    val language by viewModel.selectedLanguage.collectAsState()

    var showDisclaimerScreen by remember { mutableStateOf(false) }

    // Navigation trigger when disclaimer is already accepted
    LaunchedEffect(acceptedDisclaimer) {
        if (acceptedDisclaimer) {
            onOnboardingComplete()
        } else {
            showDisclaimerScreen = true
        }
    }

    val isUk = language == "uk"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Deep Slate
                        Color(0xFF020617)  // Near Black
                    )
                )
            )
    ) {
        if (!showDisclaimerScreen) {
            // Elegant Splash Intro Animation
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Swan-like Abstract Minimalist Icon
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .border(1.5.dp, Color(0xFFF1F5F9), RoundedCornerShape(50))
                        .background(Color(0xFF1E293B), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🦢",
                        fontSize = 48.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "THE BLACK SWAN",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Russia War Impact Monitor",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(48.dp))
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
            }
        } else {
            // Onboarding and Disclaimer Accept Screen
            val scrollState = rememberScrollState()
            var termsAccepted by remember { mutableStateOf(false) }
            var privacyAccepted by remember { mutableStateOf(false) }
            var methodologyAccepted by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp)
                    .systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (isUk) "МАНІФЕСТ ТА ЗАСТЕРЕЖЕННЯ" else "MANIFESTO & DISCLAIMER",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Beautiful Card representing the required text concept on page 8
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security Alert",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isUk) "Важливе юридичне повідомлення" else "Important Regulatory Statement",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Text from PDF (9.3 Onboarding / Disclaimer)
                        Text(
                            text = if (isUk) {
                                "Цей додаток надає відтерміновану, задокументовану інформацію з відкритих джерел лише з узагальненими локаціями. Він не надає військових розвідувальних даних у реальному часі, оперативних вказівок, даних для цілевказання або точних координат. Україна та окуповані українські території виключені з набору даних та інформаційних шарів карти."
                            } else {
                                "This app presents delayed, documented open-source information with generalized locations only. It does not provide real-time military intelligence, operational guidance, targeting data, or exact coordinates. Ukraine and occupied Ukrainian territories are excluded from the dataset and map event layers."
                            },
                            fontSize = 13.sp,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Justify
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (isUk) "Будь ласка, ознайомтесь та підтвердіть згоду:" else "Please review and accept terms to proceed:",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Checkbox 1: Terms of Use
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = termsAccepted,
                        onCheckedChange = { termsAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            uncheckedColor = Color(0xFF475569),
                            checkmarkColor = Color(0xFF0F172A)
                        )
                    )
                    Text(
                        text = if (isUk) "Я приймаю Умови використання" else "I accept the Terms of Use",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Checkbox 2: Privacy Policy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = privacyAccepted,
                        onCheckedChange = { privacyAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            uncheckedColor = Color(0xFF475569),
                            checkmarkColor = Color(0xFF0F172A)
                        )
                    )
                    Text(
                        text = if (isUk) "Я погоджуюся з Політикою конфіденційності" else "I agree to the Privacy Policy",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Checkbox 3: Methodology Disclaimer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = methodologyAccepted,
                        onCheckedChange = { methodologyAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color.White,
                            uncheckedColor = Color(0xFF475569),
                            checkmarkColor = Color(0xFF0F172A)
                        )
                    )
                    Text(
                        text = if (isUk) "Я приймаю методологічні застереження" else "I accept the Methodology disclaimer",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Accept button
                Button(
                    onClick = {
                        if (termsAccepted && privacyAccepted && methodologyAccepted) {
                            viewModel.acceptDisclaimer()
                        }
                    },
                    enabled = termsAccepted && privacyAccepted && methodologyAccepted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF020617),
                        disabledContainerColor = Color(0xFF1E293B),
                        disabledContentColor = Color(0xFF475569)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isUk) "ПОЧАТИ РОБОТУ" else "ACCEPT & START",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
