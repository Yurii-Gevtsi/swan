package com.gysignalstudio.blackswan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gysignalstudio.blackswan.ui.viewmodel.OsintViewModel

private const val TERMS_URL = "https://gy-signal-studio.web.app/terms.html"
private const val PRIVACY_URL = "https://gy-signal-studio.web.app/privacy-policy.html"
private const val METHODOLOGY_URL = "https://gy-signal-studio.web.app/methodology.html"

@Composable
fun SplashScreen(
    viewModel: OsintViewModel,
    onOnboardingComplete: () -> Unit
) {
    val acceptedDisclaimer by viewModel.acceptedDisclaimer.collectAsState()
    val language by viewModel.selectedLanguage.collectAsState()
    val isUk = language == "uk"
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(acceptedDisclaimer) {
        if (acceptedDisclaimer) {
            onOnboardingComplete()
        }
    }

    val scrollState = rememberScrollState()
    var termsAccepted by remember { mutableStateOf(false) }
    var limitationsAccepted by remember { mutableStateOf(false) }
    val canContinue = termsAccepted && limitationsAccepted

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isUk) "ВАЖЛИВЕ ПОВІДОМЛЕННЯ" else "IMPORTANT NOTICE",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isUk) "Перед продовженням" else "Before You Continue",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFEF4444),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUk) "Нейтральне інформаційне застереження" else "App Information Notice",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = if (isUk) {
                            "Black Swan показує відкладену, задокументовану інформацію з публічно доступних джерел.\n\n" +
                                "Локації подій узагальнені. Застосунок не надає військової розвідки в реальному часі, оперативних вказівок, інформації для наведення, точних координат, навігації або екстрених сповіщень.\n\n" +
                                "Україна та тимчасово окуповані території України виключені з маркерів подій і картографічних наборів даних застосунку."
                        } else {
                            "Black Swan presents delayed, documented information from publicly available sources.\n\n" +
                                "Event locations are generalized. The app does not provide real-time military intelligence, operational guidance, targeting information, exact coordinates, navigation, or emergency alerts.\n\n" +
                                "Ukraine and the temporarily occupied territories of Ukraine are excluded from event markers and mapped datasets."
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = if (isUk) "Будь ласка, перегляньте документи:" else "Please review the following documents:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            DocumentLink(
                label = if (isUk) "Умови використання" else "Terms of Use",
                onClick = { uriHandler.openUri(TERMS_URL) }
            )
            DocumentLink(
                label = if (isUk) "Політика конфіденційності" else "Privacy Policy",
                onClick = { uriHandler.openUri(PRIVACY_URL) }
            )
            DocumentLink(
                label = if (isUk) "Джерела даних і методологія" else "Data Sources and Methodology",
                onClick = { uriHandler.openUri(METHODOLOGY_URL) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            ConsentRow(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it },
                text = if (isUk) "Я погоджуюся з Умовами використання." else "I agree to the Terms of Use."
            )

            ConsentRow(
                checked = limitationsAccepted,
                onCheckedChange = { limitationsAccepted = it },
                text = if (isUk) {
                    "Я розумію, що інформація може бути відкладеною, приблизною, неповною, спірною або згодом виправленою, і її не можна використовувати для оперативних, військових, навігаційних або екстрених рішень."
                } else {
                    "I understand that the information may be delayed, approximate, incomplete, disputed, or later corrected, and must not be used for operational, military, navigation, or emergency decisions."
                }
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { if (canContinue) viewModel.acceptDisclaimer() },
                enabled = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isUk) "ПОГОДИТИСЯ Й ПРОДОВЖИТИ" else "AGREE & CONTINUE",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DocumentLink(
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color(0xFF38BDF8),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.OpenInNew,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = MaterialTheme.colorScheme.outline,
                checkmarkColor = MaterialTheme.colorScheme.surface
            )
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .padding(start = 4.dp, top = 12.dp)
                .weight(1f)
        )
    }
}
