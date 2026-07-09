package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import com.example.ui.viewmodel.OsintViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: OsintViewModel
) {
    val language by viewModel.selectedLanguage.collectAsState()
    val themeSelection by viewModel.themeSelection.collectAsState()
    val adsDisabled by viewModel.adsDisabled.collectAsState()

    val context = LocalContext.current
    val isUk = language == "uk"

    // Dialog sheets states
    var showMethodology by remember { mutableStateOf(false) }
    var showPrivacyTerms by remember { mutableStateOf(false) }
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
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF090D16))
            )
        },
        containerColor = Color(0xFF090D16)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Language Selection Section (page 13)
            Text(
                text = if (isUk) "МОВА ДОДАТКУ" else "APPLICATION LANGUAGE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF64748B)
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

            // Premium Billing Simulated Upgrades (page 13, 24)
            Text(
                text = if (isUk) "АДМІНІСТРАТИВНІ ПОСЛУГИ (MOCK BILLING)" else "UPGRADE SUBSCRIPTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF64748B)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, if (adsDisabled) Color(0xFF10B981) else Color(0xFF1E293B), RoundedCornerShape(12.dp)),
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
                                color = Color.White
                            )
                            Text(
                                text = if (adsDisabled) {
                                    if (isUk) "Преміум статус активний" else "Premium active (All ads disabled)"
                                } else {
                                    if (isUk) "Підтримайте розробку" else "Support development and hide ads"
                                },
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!adsDisabled) {
                        Button(
                            onClick = { showAdFreePurchase = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF020617)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = if (isUk) "ЗВІЛЬНИТИСЬ ВІД РЕКЛАМИ" else "PURCHASE AD-FREE", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.toggleAdFree(false) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = BorderStroke(1.dp, Color(0xFFEF4444))
                        ) {
                            Text(text = if (isUk) "Скинути преміум (Для тестів)" else "Reset Premium (For testing)", fontFamily = FontFamily.Monospace)
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
                color = Color(0xFF64748B)
            )

            SettingsLinkCard(
                title = if (isUk) "Методологія та відкриті джерела" else "Methodology & Verification",
                icon = Icons.Default.Info,
                onClick = { showMethodology = true }
            )

            SettingsLinkCard(
                title = if (isUk) "Конфіденційність та правила користування" else "Privacy Policy & Terms",
                icon = Icons.Default.Security,
                onClick = { showPrivacyTerms = true }
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
                color = Color(0xFF64748B)
            )

            Button(
                onClick = {
                    viewModel.clearCacheAndReset()
                    // Prompt feedback
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Cached, contentDescription = "Clear Cache", tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = if (isUk) "ОЧИСТИТИ КЕШ ТА СИНХРОНІЗУВАТИ" else "CLEAR CACHE & SYNC", fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- METHODOLOGY SHEET ---
        if (showMethodology) {
            Dialog(onDismissRequest = { showMethodology = false }) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
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
                            color = Color.White,
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
                            color = Color(0xFFCBD5E1),
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showMethodology = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF020617)),
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
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
                            color = Color.White,
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
                            color = Color(0xFFCBD5E1),
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showPrivacyTerms = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF020617)),
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
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
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // REQUIRED DISCLAIMER (page 24, 25)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF475569), RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isUk) {
                                    "Цей додаток не обробляє пожертвування. Посилання відкривають зовнішні веб-сайти, якими керують незалежні організації. Будь ласка, перевірте кожну організацію перед тим, як робити пожертву."
                                } else {
                                    "This app does not process donations. Links open external websites operated by independent organizations. Please review each organization before donating."
                                },
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1),
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

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { showSupportUkraine = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF020617)),
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
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (isUk) "ОБЕРІТЬ ВАРІАНТ ПІДПИСКИ" else "CHOOSE AD-FREE PLAN",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        PurchasePlanRow(
                            title = if (isUk) "Щомісячно" else "Monthly Subscription",
                            price = "$1.99 / mo",
                            onClick = {
                                viewModel.toggleAdFree(true)
                                showAdFreePurchase = false
                            }
                        )

                        PurchasePlanRow(
                            title = if (isUk) "Щорічно" else "Yearly Subscription",
                            price = "$14.99 / yr",
                            onClick = {
                                viewModel.toggleAdFree(true)
                                showAdFreePurchase = false
                            }
                        )

                        PurchasePlanRow(
                            title = if (isUk) "Пожиттєво" else "Lifetime Ad-Free Access",
                            price = "$24.99 - One time",
                            onClick = {
                                viewModel.toggleAdFree(true)
                                showAdFreePurchase = false
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isUk) {
                                "Підписки симулюються за допомогою Google Play Billing. Кошти не списуються."
                            } else {
                                "Subscriptions are simulated utilizing standard Google Play Billing API hooks. No actual charges are made."
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
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
            containerColor = if (isActive) Color.White else Color(0xFF0F172A),
            contentColor = if (isActive) Color(0xFF020617) else Color.White
        ),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (isActive) Color.White else Color(0xFF334155))
    ) {
        Text(text = label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun SettingsLinkCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color = Color.White,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = tintColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Icon(imageVector = Icons.Default.ArrowForwardIos, contentDescription = "Open", tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
        }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
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
                Text(text = name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = description, fontSize = 11.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = Icons.Default.Launch, contentDescription = "Launch site", tint = Color.White, modifier = Modifier.size(16.dp))
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(text = price, fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Mock Purchase", fontSize = 11.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
        }
    }
}
