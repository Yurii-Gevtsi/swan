package com.gysignalstudio.blackswan.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.gysignalstudio.blackswan.ui.viewmodel.SyncState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gysignalstudio.blackswan.ads.AdMobManager
import com.gysignalstudio.blackswan.ui.viewmodel.OsintViewModel

private const val STUDIO_SITE_URL = "https://gy-signal-studio.web.app"
private const val METHODOLOGY_URL = "$STUDIO_SITE_URL/methodology.html"
private const val PRIVACY_POLICY_URL = "$STUDIO_SITE_URL/privacy-policy.html"
private const val TERMS_URL = "$STUDIO_SITE_URL/terms.html"
private const val REPORT_ERROR_EMAIL = "gysignalstudio.dev@gmail.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: OsintViewModel,
    onPurchaseRemoveAds: () -> Unit,
    onRestorePurchases: () -> Unit,
) {
    val language by viewModel.selectedLanguage.collectAsState()
    val themeSelection by viewModel.themeSelection.collectAsState()
    val adsDisabled by viewModel.adsDisabled.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    val context = LocalContext.current
    val isUk = language == "uk"

    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncState.Success -> Toast.makeText(
                context,
                if (isUk) "Синхронізовано, дані оновлено" else "Synced, data updated",
                Toast.LENGTH_SHORT
            ).show()
            is SyncState.Error -> Toast.makeText(
                context,
                (if (isUk) "Помилка синхронізації: " else "Sync failed: ") + state.message,
                Toast.LENGTH_LONG
            ).show()
            else -> Unit
        }
    }

    // Dialog sheets states
    var showAbout by remember { mutableStateOf(false) }
    var showMethodology by remember { mutableStateOf(false) }
    var showPrivacyTerms by remember { mutableStateOf(false) }
    var showPrivacyChoices by remember { mutableStateOf(false) }
    var privacyChoicesMessage by remember { mutableStateOf<String?>(null) }
    var showSupportUkraine by remember { mutableStateOf(false) }
    var showAdFreePurchase by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isUk) "ПРОФІЛЬ ТА НАЛАШТУВАННЯ" else "PROFILE & SETTINGS",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = if (isUk) "ВИГЛЯД" else "APPEARANCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.setTheme(if (themeSelection == "light") "dark" else "light")
                    }
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (themeSelection == "light") Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isUk) "Світла тема" else "Light theme",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isUk) "Світле оформлення застосунку" else "Use the light application palette",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = themeSelection == "light",
                        onCheckedChange = { enabled -> viewModel.setTheme(if (enabled) "light" else "dark") },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            uncheckedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            // Language Selection Section (page 13)
            Text(
                text = if (isUk) "МОВА ДОДАТКУ" else "APPLICATION LANGUAGE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageButton(
                    label = "English",
                    isActive = !isUk,
                    onClick = { viewModel.setLanguage("en") },
                    modifier = Modifier.weight(1f)
                )
                LanguageButton(
                    label = "Українська",
                    isActive = isUk,
                    onClick = { viewModel.setLanguage("uk") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Premium Billing Upgrades (page 13, 24)
            Text(
                text = if (isUk) "ПОКУПКИ GOOGLE PLAY" else "GOOGLE PLAY PURCHASES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (adsDisabled) Color(0xFF10B981) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (adsDisabled) Icons.Default.CheckCircle else Icons.Default.WorkspacePremium,
                            contentDescription = "Ad Free Status",
                            tint = if (adsDisabled) Color(0xFF10B981) else Color(0xFFEAB308),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isUk) "Відключення реклами (Ad-Free)" else "Ad-Free Premium Access",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (adsDisabled) {
                                    if (isUk) "Преміум статус активний" else "Premium active (All ads disabled)"
                                } else {
                                    if (isUk) "Підтримайте розробку" else "Support development and hide ads"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!adsDisabled) {
                        Button(
                            onClick = { showAdFreePurchase = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = if (isUk) "ЗВІЛЬНИТИСЬ ВІД РЕКЛАМИ" else "PURCHASE AD-FREE", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onRestorePurchases,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                            border = BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Text(text = if (isUk) "ВІДНОВИТИ ПОКУПКУ" else "RESTORE PURCHASE", fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Information Sheets & Supporting (page 13)
            Text(
                text = if (isUk) "ДОКУМЕНТАЦІЯ ТА ЗАСОБИ ПІДТРИМКИ" else "DOCUMENTATION & RESOURCE LEVERS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SettingsLinkCard(
                title = if (isUk) "Про застосунок" else "About",
                icon = Icons.Default.Info,
                onClick = { showAbout = true }
            )

            SettingsLinkCard(
                title = if (isUk) "Джерела даних і методологія" else "Data Sources & Methodology",
                icon = Icons.Default.Public,
                onClick = { openExternalUrl(context, METHODOLOGY_URL) }
            )

            SettingsLinkCard(
                title = if (isUk) "Політика конфіденційності" else "Privacy Policy",
                icon = Icons.Default.Security,
                onClick = { openExternalUrl(context, PRIVACY_POLICY_URL) }
            )

            SettingsLinkCard(
                title = if (isUk) "Умови використання" else "Terms of Use",
                icon = Icons.Default.Gavel,
                onClick = { openExternalUrl(context, TERMS_URL) }
            )

            SettingsLinkCard(
                title = if (isUk) "Налаштування конфіденційності" else "Privacy Choices",
                icon = Icons.Default.Tune,
                onClick = {
                    val activity = context as? Activity
                    if (activity == null) {
                        privacyChoicesMessage = if (isUk) "\u041d\u0430\u043b\u0430\u0448\u0442\u0443\u0432\u0430\u043d\u043d\u044f \u043a\u043e\u043d\u0444\u0456\u0434\u0435\u043d\u0446\u0456\u0439\u043d\u043e\u0441\u0442\u0456 \u043d\u0435\u0434\u043e\u0441\u0442\u0443\u043f\u043d\u0456 \u0432 \u0446\u044c\u043e\u043c\u0443 \u043a\u043e\u043d\u0442\u0435\u043a\u0441\u0442\u0456." else "Privacy choices are unavailable in this context."
                        showPrivacyChoices = true
                    } else {
                        AdMobManager.showPrivacyOptions(activity) { wasShown, error ->
                            if (!wasShown || error != null) {
                                privacyChoicesMessage = error ?: if (isUk) "\u0414\u043b\u044f \u0432\u0430\u0448\u043e\u0433\u043e \u0440\u0435\u0433\u0456\u043e\u043d\u0443 \u043e\u043a\u0440\u0435\u043c\u0430 \u0444\u043e\u0440\u043c\u0430 \u043a\u043e\u043d\u0444\u0456\u0434\u0435\u043d\u0446\u0456\u0439\u043d\u043e\u0441\u0442\u0456 \u043d\u0435 \u043f\u043e\u0442\u0440\u0456\u0431\u043d\u0430." else "No separate privacy options form is required for your region."
                                showPrivacyChoices = true
                            }
                        }
                    }
                }
            )

            SettingsLinkCard(
                title = if (isUk) "Повідомити про помилку" else "Report an Error",
                icon = Icons.Default.Email,
                onClick = { sendErrorReport(context) }
            )

            SettingsLinkCard(
                title = if (isUk) "ПІДТРИМАТИ УКРАЇНУ (ОФІЦІЙНІ ФОНДИ)" else "SUPPORT UKRAINE (OFFICIAL)",
                icon = Icons.Default.HeartBroken,
                tintColor = Color(0xFFEAB308),
                onClick = { showSupportUkraine = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Clear Cache (page 22, 23)
            Text(
                text = if (isUk) "СЕРВІСНІ СИСТЕМНІ ОПЕРАЦІЇ" else "SYSTEM MAINTENANCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { viewModel.clearCacheAndReset() },
                enabled = syncState != SyncState.Syncing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (syncState == SyncState.Syncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Icon(imageVector = Icons.Default.Cached, contentDescription = "Clear Cache", tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = if (isUk) "ОЧИСТИТИ КЕШ ТА СИНХРОНІЗУВАТИ" else "CLEAR CACHE & SYNC", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showAbout) {
            LegalInformationDialog(
                title = if (isUk) "Про Black Swan: War Impact Map" else "About Black Swan: War Impact Map",
                body = if (isUk) {
                    "Black Swan: War Impact Map публікується GY Signal Studio, проєктом незалежного розробника Yurii Gevtsi в Україні. Це незалежний та неофіційний довідник з відкритими джерелами й узагальненими географічними даними. Застосунок не пов'язаний з урядами, військовими структурами, розвідкою, благодійними організаціями, медіа або Google і не є трекером подій у реальному часі."
                } else {
                    "Black Swan: War Impact Map is published by GY Signal Studio, an independent developer project operated by Yurii Gevtsi in Ukraine. It is an independent, unofficial reference application with public sources and generalized locations; it is not affiliated with governments, military organisations, intelligence agencies, charities, media organisations, or Google."
                },
                onDismiss = { showAbout = false },
                dismissLabel = if (isUk) "Закрити" else "Close"
            )
        }

        if (showPrivacyChoices) {
            LegalInformationDialog(
                title = if (isUk) "Налаштування конфіденційності" else "Privacy Choices",
                body = privacyChoicesMessage ?: if (isUk) "\u041d\u0430\u043b\u0430\u0448\u0442\u0443\u0432\u0430\u043d\u043d\u044f \u043a\u043e\u043d\u0444\u0456\u0434\u0435\u043d\u0446\u0456\u0439\u043d\u043e\u0441\u0442\u0456 \u043a\u0435\u0440\u0443\u044e\u0442\u044c\u0441\u044f Google UMP \u0432\u0456\u0434\u043f\u043e\u0432\u0456\u0434\u043d\u043e \u0434\u043e \u0432\u0430\u0448\u043e\u0433\u043e \u0440\u0435\u0433\u0456\u043e\u043d\u0443." else "Privacy choices are managed by Google UMP for your region.",
                onDismiss = { showPrivacyChoices = false },
                dismissLabel = if (isUk) "Закрити" else "Close"
            )
        }

        // --- METHODOLOGY SHEET ---
        if (showMethodology) {
            Dialog(onDismissRequest = { showMethodology = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (isUk) "МЕТОДОЛОГІЯ OSINT" else "OSINT METHODOLOGY",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isUk) {
                                "Дані у цьому додатку збираються виключно з публічних верифікованих звітів авторитетних установ (RUSI, Bellingcat, Lloyd's List, CSIS та KSE Institute).\n\n" +
                                "Основні принципи методології:\n" +
                                "1. ВІДТЕРМІНОВАНІСТЬ: Жодні відомості не оприлюднюються «наживо» чи у реальному часі. Усі записи затримуються мінімум на 48 годин.\n" +
                                "2. НЕЙТРАЛЬНІСТЬ: Ми використовуємо суху аналітичну термінологію, уникаючи оціночних ярликів («успішний удар», «знищено окупантів» тощо).\n" +
                                "3. БЕЗПЕКА ГЕОГРАФІЇ: Точні координати ніколи не зберігаються та не відображаються. Будь-які маркери посилаються на узагальнені регіональні кола радіусом щонайменше 50-100 км.\n" +
                                "4. ВИКЛЮЧЕННЯ УКРАЇНИ: Територія України повністю виключена з аналізу. Жодні події не фіксуються в межах суверенних українських кордонів, включаючи тимчасово окуповані території."
                            } else {
                                "The data presented in this monitor is sourced strictly from verified, published open source documents by reputable strategic research institutes (RUSI, Bellingcat, Lloyd's List, CSIS, and KSE Institute).\n\n" +
                                "Core Methodology Pillars:\n" +
                                "1. DELAYED REPORTING: No real-time tracker. All data layers represent historical records, published with at least 48 hours of delay to enforce regulatory compliance.\n" +
                                "2. OBJECTIVE LEXICON: Strict adherence to neutral documentation, avoiding emotional, sensationalist, or breaking-news prefixes.\n" +
                                "3. GENERALIZED COORDINATES: Exact strike coordinates, facility boundaries, and berths are omitted. Locations map strictly to regional centroids with minimum 50km error margin.\n" +
                                "4. UKRAINE DATA EXCLUSION: Sovereign territory of Ukraine (including occupied areas) has no overlay data layers, preserving absolute geopolitical neutrality."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showMethodology = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(text = if (isUk) "Зрозуміло" else "Acknowledge")
                        }
                    }
                }
            }
        }

        // --- PRIVACY POLICY SHEET ---
        if (showPrivacyTerms) {
            Dialog(onDismissRequest = { showPrivacyTerms = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (isUk) "КОНФІДЕНЦІЙНІСТЬ ТА ПРАВИЛА" else "PRIVACY & SAFETY POLICY",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isUk) {
                                "1. ПРИВАТНІСТЬ ДАНИХ: Додаток працює повністю локально. Жодні профілі користувачів чи дані про перегляд не збираються та не передаються на сервери.\n\n" +
                                "2. ДЖЕРЕЛО ДАНИХ: Увесь обмін інформацією базується на статичних задокументованих файлах маніфесту без використання живих веб-сокетів чи пуш-сповіщень.\n\n" +
                                "3. ВІДПОВІДАЛЬНІСТЬ: Додаток надається виключно в академічних та освітньо-документальних цілях. Він не містить розвідданих та не може використовуватись для оперативного планування."
                            } else {
                                "1. OFFLINE EXCLUSIVE: All profile calculations remain fully sandboxed inside the client's local storage. No analytics telemetry or location search tracking is compiled on external servers.\n\n" +
                                "2. STATIC DELIVERY: Data sync utilizes strictly delayed public snapshots over standard HTTPS manifest architecture. No websockets or push servers are implemented.\n\n" +
                                "3. DOCUMENTARY USE ONLY: Provided solely for educational, academic, and policy research purposes. It possesses zero tactical utility."
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showPrivacyTerms = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(text = if (isUk) "Закрити" else "Close")
                        }
                    }
                }
            }
        }

        // --- SUPPORT UKRAINE DIALOG (page 24, 25) ---
        if (showSupportUkraine) {
            Dialog(onDismissRequest = { showSupportUkraine = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (isUk) "ПІДТРИМКА УКРАЇНИ" else "SUPPORT UKRAINE",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // REQUIRED DISCLAIMER (page 24, 25)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isUk) {
                                    "Цей додаток не обробляє пожертвування. Посилання відкривають зовнішні веб-сайти, якими керують незалежні організації. Будь ласка, перевірте кожну організацію перед тим, як робити пожертву."
                                } else {
                                    "This app does not process donations. Links open external websites operated by independent organizations. Please review each organization before donating."
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Fund 1: United24
                        FundLinkRow(
                            name = "United24",
                            description = if (isUk) "Офіційна державна фандрейзингова платформа України." else "The official fundraising platform of Ukraine.",
                            url = "https://u24.gov.ua",
                            context = context
                        )

                        // Fund 2: Come Back Alive
                        FundLinkRow(
                            name = "Come Back Alive",
                            description = if (isUk) "Фонд компетентної допомоги українській армії." else "The competent assistance foundation for the defense of Ukraine.",
                            url = "https://savelife.in.ua",
                            context = context
                        )

                        FundLinkRow(
                            name = if (isUk) "Фонд Стерненка" else "Sternenko Fund",
                            description = if (isUk) "Благодійний фонд Сергія Стерненка для підтримки Сил оборони України." else "Serhii Sternenko's charitable fund supporting Ukraine's Defense Forces.",
                            url = "https://www.sternenkofund.org/donate",
                            context = context
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showSupportUkraine = false },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(text = if (isUk) "Назад" else "Back")
                        }
                    }
                }
            }
        }

        // --- AD FREE PURCHASE DIALOG (page 13, 24) ---
        if (showAdFreePurchase) {
            Dialog(onDismissRequest = { showAdFreePurchase = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (isUk) "ВИМКНУТИ РЕКЛАМУ" else "REMOVE ADS",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        PurchasePlanRow(
                            title = if (isUk) "Одноразова покупка" else "One-time purchase",
                            price = "$1",
                            onClick = {
                                onPurchaseRemoveAds()
                                showAdFreePurchase = false
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isUk) {
                                "Покупка обробляється через Google Play Billing. Після підтвердження покупка відновлюється через Google Play ownership."
                            } else {
                                "The purchase is handled by Google Play Billing. Once acknowledged, ad-free access is restored from Google Play ownership."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                onRestorePurchases()
                                showAdFreePurchase = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = if (isUk) "Відновити покупку" else "Restore purchase")
                        }

                        TextButton(
                            onClick = { showAdFreePurchase = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = if (isUk) "Скасувати" else "Cancel")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(45.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun SettingsLinkCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color? = null,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = tintColor ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Icon(imageVector = Icons.Default.ArrowForwardIos, contentDescription = "Open", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun LegalInformationDialog(
    title: String,
    body: String,
    dismissLabel: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}

private fun openExternalUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

private fun sendErrorReport(context: android.content.Context) {
    runCatching {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$REPORT_ERROR_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "Black Swan: War Impact Map - error report")
        }
        context.startActivity(intent)
    }
}

@Composable
fun FundLinkRow(
    name: String,
    description: String,
    url: String,
    context: android.content.Context
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Handled
                }
            },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.Launch, contentDescription = "Launch site", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun PurchasePlanRow(
    title: String,
    price: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = price, fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Mock Purchase", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
    }
}
