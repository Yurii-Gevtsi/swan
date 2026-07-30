package com.gysignalstudio.blackswan.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gysignalstudio.blackswan.R
import com.gysignalstudio.blackswan.data.local.AssetDataLoader
import com.gysignalstudio.blackswan.data.model.SpecialOperationEntry
import com.gysignalstudio.blackswan.data.model.SpecialOperationsSnapshot
import com.gysignalstudio.blackswan.ui.viewmodel.OsintViewModel
import java.text.NumberFormat
import java.util.Locale

private const val OFFICIAL_SOURCE = "https://www.zsu.gov.ua/oriientovni-vtraty-protyvnyka"

private enum class TotalTab {
    TOTAL,
    SPECIAL_OPERATIONS
}

private data class LossTotal(
    val en: String,
    val uk: String,
    val value: Long,
    @DrawableRes val iconRes: Int,
)

private data class OperationVisual(
    val accent: Color,
    val chipBackground: Color,
    val icon: ImageVector? = null,
    @DrawableRes val iconRes: Int? = null,
)

private val lossTotals = listOf(
    LossTotal("Personnel", "Особовий склад", 1_422_970, R.drawable.loss_personnel),
    LossTotal("Tanks", "Танки", 12_141, R.drawable.loss_tanks),
    LossTotal("Armored combat vehicles", "Бойові броньовані машини", 24_938, R.drawable.loss_armored_vehicles),
    LossTotal("Artillery systems", "Артилерійські системи", 45_953, R.drawable.loss_artillery),
    LossTotal("Multiple-launch rocket systems", "Реактивні системи залпового вогню", 1_936, R.drawable.loss_mlrs),
    LossTotal("Air-defense systems", "Засоби протиповітряної оборони", 1_492, R.drawable.loss_air_defense),
    LossTotal("Aircraft", "Літаки", 437, R.drawable.loss_aircraft),
    LossTotal("Helicopters", "Гелікоптери", 353, R.drawable.loss_helicopters),
    LossTotal("Unmanned aerial vehicles", "Безпілотні літальні апарати", 409_204, R.drawable.loss_uav),
    LossTotal("Cruise missiles", "Крилаті ракети", 4_906, R.drawable.loss_cruise_missiles),
    LossTotal("Ships and boats", "Кораблі та катери", 34, R.drawable.loss_ships),
    LossTotal("Submarines", "Підводні човни", 2, R.drawable.loss_submarines),
    LossTotal("Vehicles and fuel tanks", "Автомобілі та автоцистерни", 120_307, R.drawable.loss_transport),
    LossTotal("Special equipment", "Спеціальна техніка", 4_420, R.drawable.loss_special_equipment),
    LossTotal("Ground robotic systems", "Наземні робототехнічні комплекси", 1_907, R.drawable.loss_ground_robots),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotalScreen(viewModel: OsintViewModel) {
    val language by viewModel.selectedLanguage.collectAsState()
    val isUk = language == "uk"
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val format = remember(isUk) {
        NumberFormat.getIntegerInstance(if (isUk) Locale.forLanguageTag("uk-UA") else Locale.US)
    }
    var selectedTab by rememberSaveable { mutableStateOf(TotalTab.TOTAL) }

    val specialOperationsSnapshot by produceState<SpecialOperationsSnapshot?>(initialValue = null, context) {
        value = AssetDataLoader.loadSpecialOperations(context)
    }

    val title = when (selectedTab) {
        TotalTab.TOTAL -> if (isUk) "ЗАГАЛЬНІ ВТРАТИ" else "TOTAL"
        TotalTab.SPECIAL_OPERATIONS -> if (isUk) "СПЕЦІАЛЬНІ ОПЕРАЦІЇ" else "SPECIAL OPERATIONS"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                listOf(TotalTab.TOTAL, TotalTab.SPECIAL_OPERATIONS).forEach { tab ->
                    val label = when (tab) {
                        TotalTab.TOTAL -> if (isUk) "Загальні втрати" else "Total"
                        TotalTab.SPECIAL_OPERATIONS -> if (isUk) "Спеціальні операції" else "Special operations"
                    }
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    )
                }
            }

            when (selectedTab) {
                TotalTab.TOTAL -> TotalLossesContent(
                    isUk = isUk,
                    format = format,
                    uriHandler = uriHandler,
                )
                TotalTab.SPECIAL_OPERATIONS -> SpecialOperationsContent(
                    isUk = isUk,
                    snapshot = specialOperationsSnapshot,
                    uriHandler = uriHandler,
                )
            }
        }
    }
}

@Composable
private fun TotalLossesContent(
    isUk: Boolean,
    format: NumberFormat,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                Text(
                    text = if (isUk) "Орієнтовні втрати противника з 24.02.2022" else "Estimated enemy losses since 24 February 2022",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (isUk) "Станом на 15 липня 2026 року · зростаючим підсумком" else "As of 15 July 2026 · cumulative totals",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        items(lossTotals) { loss ->
            LossTotalCard(loss = loss, isUk = isUk, format = format)
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .clickable { uriHandler.openUri(OFFICIAL_SOURCE) },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Source,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(22.dp),
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            text = if (isUk) "Офіційне джерело" else "Official source",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (isUk) "Збройні Сили України · відкрите" else "Armed Forces of Ukraine · open",
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Text(
                text = if (isUk) {
                    "Показники є орієнтовними та можуть уточнюватися Генеральним штабом ЗСУ."
                } else {
                    "Figures are estimates and may be revised by the General Staff of the Armed Forces of Ukraine."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SpecialOperationsContent(
    isUk: Boolean,
    snapshot: SpecialOperationsSnapshot?,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    val operations = snapshot?.operations.orEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(10.dp)) }

        if (operations.isEmpty()) {
            item {
                Text(
                    text = if (isUk) "Дані спецоперацій зараз недоступні." else "Special operations data is not available right now.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        } else {
            items(operations, key = { it.id }) { operation ->
                SpecialOperationCard(
                    operation = operation,
                    isUk = isUk,
                    uriHandler = uriHandler,
                )
            }
        }
    }
}

@Composable
private fun LossTotalCard(
    loss: LossTotal,
    isUk: Boolean,
    format: NumberFormat,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(loss.iconRes),
                    contentDescription = if (isUk) loss.uk else loss.en,
                    modifier = Modifier.size(27.dp),
                )
            }
            Text(
                text = if (isUk) loss.uk else loss.en,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            Text(
                text = (if (loss.en == "Personnel") "≈ " else "") + format.format(loss.value),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun SpecialOperationCard(
    operation: SpecialOperationEntry,
    isUk: Boolean,
    uriHandler: androidx.compose.ui.platform.UriHandler,
) {
    val visual = operationVisual(operation.id)
    val details = if (isUk) operation.detailsUk else operation.detailsEn

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(visual.chipBackground, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (visual.iconRes != null) {
                        Image(
                            painter = painterResource(visual.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = requireNotNull(visual.icon),
                            contentDescription = null,
                            tint = visual.accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        text = if (isUk) operation.titleUk else operation.titleEn,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (isUk) operation.dateLabelUk else operation.dateLabelEn,
                        color = visual.accent,
                        fontSize = 12.sp,
                    )
                }
            }

            OperationSection(
                title = if (isUk) "Ціль" else "Target",
                value = if (isUk) operation.targetUk else operation.targetEn,
            )
            OperationSection(
                title = if (isUk) "Наслідки" else "Impact",
                value = if (isUk) operation.impactUk else operation.impactEn,
            )

            operation.metrics.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isUk) item.labelUk else item.labelEn,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (isUk) item.valueUk else item.valueEn ?: item.valueUk,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            if (details.isNotEmpty()) {
                Text(
                    text = if (isUk) "Деталі" else "Details",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                details.forEach { detail ->
                    Text(
                        text = "- $detail",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            operation.sourceUrls.forEachIndexed { index, url ->
                OutlinedButton(
                    onClick = { uriHandler.openUri(url) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = if (index == 0) Icons.Default.OpenInNew else Icons.Default.Article,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isUk) "Джерело ${index + 1}" else "Source ${index + 1}")
                }
            }
        }
    }
}

@Composable
private fun OperationSection(
    title: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

private fun operationVisual(id: String): OperationVisual {
    return when (id) {
        "operation_molochka" -> OperationVisual(
            accent = Color(0xFFF97316),
            chipBackground = Color(0xFF3F1D0C),
            icon = Icons.Default.DirectionsBoat,
        )
        "operation_spiderweb" -> OperationVisual(
            accent = Color(0xFF38BDF8),
            chipBackground = Color(0xFF111827),
            icon = Icons.Default.Flight,
        )
        "moskva_cruiser_sunk_2022" -> OperationVisual(
            accent = Color(0xFF38BDF8),
            chipBackground = Color(0xFF082F49),
            iconRes = R.drawable.loss_ships,
        )
        "novorossiysk_submarine_2025" -> OperationVisual(
            accent = Color(0xFF14B8A6),
            chipBackground = Color(0xFF092F2E),
            iconRes = R.drawable.loss_submarines,
        )
        "omsk_refinery_deep_strike_2026" -> OperationVisual(
            accent = Color(0xFFF43F5E),
            chipBackground = Color(0xFF3A1020),
            icon = Icons.Default.Build,
        )
        "toropets_grau_arsenal_2024" -> OperationVisual(
            accent = Color(0xFFF59E0B),
            chipBackground = Color(0xFF3B2A05),
            icon = Icons.Default.Warning,
        )
        "mordovia_container_radar_2024" -> OperationVisual(
            accent = Color(0xFF34D399),
            chipBackground = Color(0xFF0B2C22),
            icon = Icons.Default.Security,
        )
        else -> OperationVisual(
            accent = Color(0xFFA78BFA),
            chipBackground = Color(0xFF24163D),
            icon = Icons.Default.Star,
        )
    }
}
