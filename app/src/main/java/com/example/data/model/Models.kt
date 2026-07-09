package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// --- ENUMS ---

enum class OsintCategory {
    INFRASTRUCTURE_DISRUPTION,
    FUEL_SUPPLY_DISRUPTION,
    REGIONAL_FISCAL_STRESS,
    LOGISTICS_PRESSURE,
    INDUSTRIAL_DISRUPTION,
    PUBLIC_CASUALTY_DOCUMENTATION,
    SANCTIONS_IMPACT,
    OFFICIAL_STATEMENT,
    ECONOMIC_INDICATOR_UPDATE,
    MARITIME_ASSET_DISRUPTION,
    NAVAL_VESSEL_DAMAGE,
    NAVAL_VESSEL_LOSS,
    SHADOW_FLEET_DISRUPTION,
    SHADOW_FLEET_SANCTIONS,
    VESSEL_SEIZURE_OR_DETENTION,
    VESSEL_DEREGISTRATION,
    PORT_LOGISTICS_DISRUPTION,
    ENERGY_EXPORT_DISRUPTION,
    CORRECTION,
    RETRACTION
}

enum class EventScope {
    TERRITORIAL_RUSSIA,
    MARITIME,
    PORT_INFRASTRUCTURE,
    ENERGY_EXPORT,
    SANCTIONS_ENFORCEMENT,
    LOGISTICS_ROUTE,
    MILITARY_ASSET,
    ECONOMIC_INDICATOR,
    REGIONAL_INDICATOR,
    GLOBAL_SHIPPING
}

enum class Theater {
    RUSSIA_INTERNAL,
    BLACK_SEA,
    AZOV_SEA,
    BALTIC_SEA,
    NORTH_SEA,
    CASPIAN_REGION,
    EUROPEAN_WATERS,
    INTERNATIONAL_WATERS,
    GLOBAL_SHIPPING,
    SANCTIONS_JURISDICTION,
    UNKNOWN_OR_GENERAL
}

enum class ActorAttribution {
    UKRAINIAN_DEFENSE_FORCES,
    UKRAINIAN_NAVY,
    SECURITY_SERVICE_OF_UKRAINE,
    UKRAINIAN_MILITARY_INTELLIGENCE,
    UNKNOWN_ACTOR,
    UNATTRIBUTED,
    SANCTIONS_AUTHORITY,
    EU_AUTHORITY,
    US_AUTHORITY,
    UK_AUTHORITY,
    LOCAL_GOVERNMENT,
    RUSSIAN_OFFICIAL_STATEMENT,
    NATURAL_OR_ACCIDENT_REPORTED
}

enum class VerificationStatus {
    OFFICIAL_CONFIRMED,
    INSTITUTIONAL_CONFIRMED,
    MULTI_SOURCE_CONFIRMED,
    OFFICIAL_STATEMENT_ONLY,
    MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE,
    ECONOMIC_DATA_CONFIRMED,
    UPDATED,
    CORRECTED,
    RETRACTED
}

enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    SYSTEMIC,
    UNKNOWN
}

// --- ROOM ENTITIES ---

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val status: String,
    val titleEn: String,
    val titleUk: String,
    val date: String, // YYYY-MM-DD
    val datePrecision: String, // DAY
    val category: String,
    val eventScope: String,
    val theater: String,
    val regionId: String?,
    val federalDistrictId: String?,
    val maritimeAreaId: String?,
    val sanctionsJurisdictionId: String?,
    val approximateLocationLabelEn: String,
    val approximateLocationLabelUk: String,
    val lat: Double,
    val lng: Double,
    val radiusKm: Int,
    val precision: String,
    val assetId: String?,
    val actor: String,
    val actorConfidence: String,
    val actorNote: String,
    val verificationStatus: String,
    val severity: String,
    val summaryEn: String,
    val summaryUk: String,
    val impactTags: String, // Comma separated
    val sources: String, // Comma separated source IDs
    val safetyNotes: String,
    val createdAt: String,
    val updatedAt: String
)

@Entity(tableName = "regions")
data class RegionEntity(
    @PrimaryKey val regionId: String,
    val nameEn: String,
    val nameUk: String,
    val nameRu: String,
    val federalDistrictId: String,
    val lat: Double,
    val lng: Double,
    val defaultRadiusKm: Int,
    val majorCities: String, // Comma separated
    val fuelStressStatus: String, // LOW, MEDIUM, HIGH
    val fiscalStressStatus: String, // LOW, MEDIUM, HIGH
    val infrastructureEventCount: Int,
    val eventCount: Int,
    val lastUpdated: String
)

@Entity(tableName = "maritime_areas")
data class MaritimeAreaEntity(
    @PrimaryKey val maritimeAreaId: String,
    val nameEn: String,
    val nameUk: String,
    val theater: String,
    val lat: Double,
    val lng: Double,
    val defaultRadiusKm: Int,
    val displayPrecision: String,
    val eventCount: Int,
    val assetEventsCount: Int,
    val sanctionsEventsCount: Int,
    val lastUpdated: String
)

@Entity(tableName = "sanctions_jurisdictions")
data class SanctionsJurisdictionEntity(
    @PrimaryKey val sanctionsJurisdictionId: String,
    val nameEn: String,
    val nameUk: String,
    val lat: Double,
    val lng: Double,
    val defaultRadiusKm: Int,
    val displayPrecision: String
)

@Entity(tableName = "maritime_assets")
data class MaritimeAssetEntity(
    @PrimaryKey val assetId: String,
    val assetName: String,
    val assetType: String,
    val assetFlag: String,
    val assetRole: String,
    val knownAliases: String,
    val status: String,
    val statusDate: String,
    val sources: String
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val sourceId: String,
    val sourceName: String,
    val publisher: String,
    val sourceType: String,
    val country: String,
    val language: String,
    val reliabilityScore: Int,
    val lastChecked: String,
    val usedInRecordsCount: Int,
    val sourceDescription: String,
    val allowedUse: String,
    val reliabilityNote: String,
    val sourceUrl: String
)

// --- SAMPLE DATA GENERATOR ---

object SampleOsintData {
    val sources = listOf(
        SourceEntity(
            sourceId = "source_rusi",
            sourceName = "RUSI Defense Analysis",
            publisher = "Royal United Services Institute",
            sourceType = "THINK_TANK",
            country = "United Kingdom",
            language = "en",
            reliabilityScore = 5,
            lastChecked = "2026-07-08",
            usedInRecordsCount = 4,
            sourceDescription = "Specialist research reports on military logistics and sanctions evasion tactics.",
            allowedUse = "Academic and documentary analysis reference only.",
            reliabilityNote = "Highest validation standards with verified field experts.",
            sourceUrl = "https://www.rusi.org"
        ),
        SourceEntity(
            sourceId = "source_bellingcat",
            sourceName = "Bellingcat OSINT",
            publisher = "Bellingcat Investigatives",
            sourceType = "INDEPENDENT_OSINT",
            country = "Netherlands",
            language = "en",
            reliabilityScore = 5,
            lastChecked = "2026-07-07",
            usedInRecordsCount = 3,
            sourceDescription = "Open source verification of shipping tracking records, satellite imagery, and public registries.",
            allowedUse = "Public documentary citations.",
            reliabilityNote = "Highly peer-reviewed multi-angle satellite and register triangulation.",
            sourceUrl = "https://www.bellingcat.com"
        ),
        SourceEntity(
            sourceId = "source_lloyds",
            sourceName = "Lloyd's List Intelligence",
            publisher = "Informa Maritime Intelligence",
            sourceType = "MARITIME_REGISTRY",
            country = "United Kingdom",
            language = "en",
            reliabilityScore = 5,
            lastChecked = "2026-07-09",
            usedInRecordsCount = 5,
            sourceDescription = "Global shipping registries, vessel tracking database, flag deregistrations, and ownership networks.",
            allowedUse = "Commercial and maritime regulatory research data.",
            reliabilityNote = "Definitive global authority for official vessel registration data.",
            sourceUrl = "https://lloydslist.maritimeintelligence.informa.com"
        ),
        SourceEntity(
            sourceId = "source_kse",
            sourceName = "KSE Institute Sanctions Group",
            publisher = "Kyiv School of Economics",
            sourceType = "ACADEMIC",
            country = "Ukraine",
            language = "en",
            reliabilityScore = 4,
            lastChecked = "2026-07-05",
            usedInRecordsCount = 4,
            sourceDescription = "Monitoring and analysis of Russian oil export cap enforcement and shadow fleet activity.",
            allowedUse = "Academic citation and public policy research.",
            reliabilityNote = "Rigorous data analysis of corporate structures and shipping registers.",
            sourceUrl = "https://kse.ua"
        ),
        SourceEntity(
            sourceId = "source_csis",
            sourceName = "CSIS Strategic Monitoring",
            publisher = "Center for Strategic and International Studies",
            sourceType = "THINK_TANK",
            country = "United States",
            language = "en",
            reliabilityScore = 5,
            lastChecked = "2026-07-06",
            usedInRecordsCount = 3,
            sourceDescription = "Analysis of strategic infrastructure resilience, refining capacity, and regional economic stress indicators.",
            allowedUse = "Strategic policy research citation.",
            reliabilityNote = "Highly authoritative geopolitical and economic research institution.",
            sourceUrl = "https://www.csis.org"
        )
    )

    val regions = listOf(
        RegionEntity(
            regionId = "ru_rostov_oblast",
            nameEn = "Rostov Oblast",
            nameUk = "Ростовська область",
            nameRu = "Ростовская область",
            federalDistrictId = "ru_southern_federal_district",
            lat = 47.2,
            lng = 39.7,
            defaultRadiusKm = 100,
            majorCities = "Rostov-on-Don, Taganrog, Shakhty",
            fuelStressStatus = "HIGH",
            fiscalStressStatus = "MEDIUM",
            infrastructureEventCount = 4,
            eventCount = 5,
            lastUpdated = "2026-07-09"
        ),
        RegionEntity(
            regionId = "ru_krasnodar_krai",
            nameEn = "Krasnodar Krai",
            nameUk = "Краснодарський край",
            nameRu = "Краснодарский край",
            federalDistrictId = "ru_southern_federal_district",
            lat = 45.0,
            lng = 39.0,
            defaultRadiusKm = 120,
            majorCities = "Krasnodar, Sochi, Novorossiysk",
            fuelStressStatus = "MEDIUM",
            fiscalStressStatus = "LOW",
            infrastructureEventCount = 3,
            eventCount = 4,
            lastUpdated = "2026-07-08"
        ),
        RegionEntity(
            regionId = "ru_leningrad_oblast",
            nameEn = "Leningrad Oblast",
            nameUk = "Ленінградська область",
            nameRu = "Ленинградская область",
            federalDistrictId = "ru_northwestern_federal_district",
            lat = 59.9,
            lng = 31.0,
            defaultRadiusKm = 150,
            majorCities = "Gatchina, Vyborg, Ust-Luga",
            fuelStressStatus = "HIGH",
            fiscalStressStatus = "MEDIUM",
            infrastructureEventCount = 5,
            eventCount = 6,
            lastUpdated = "2026-07-09"
        ),
        RegionEntity(
            regionId = "ru_murmansk_oblast",
            nameEn = "Murmansk Oblast",
            nameUk = "Мурманська область",
            nameRu = "Мурманская область",
            federalDistrictId = "ru_northwestern_federal_district",
            lat = 68.9,
            lng = 33.0,
            defaultRadiusKm = 180,
            majorCities = "Murmansk, Severomorsk, Apatity",
            fuelStressStatus = "LOW",
            fiscalStressStatus = "HIGH",
            infrastructureEventCount = 1,
            eventCount = 2,
            lastUpdated = "2026-07-06"
        ),
        RegionEntity(
            regionId = "ru_belgorod_oblast",
            nameEn = "Belgorod Oblast",
            nameUk = "Бєлгородська область",
            nameRu = "Белгородская область",
            federalDistrictId = "ru_central_federal_district",
            lat = 50.6,
            lng = 36.6,
            defaultRadiusKm = 80,
            majorCities = "Belgorod, Stary Oskol",
            fuelStressStatus = "HIGH",
            fiscalStressStatus = "HIGH",
            infrastructureEventCount = 6,
            eventCount = 8,
            lastUpdated = "2026-07-09"
        ),
        RegionEntity(
            regionId = "ru_kursk_oblast",
            nameEn = "Kursk Oblast",
            nameUk = "Курська область",
            nameRu = "Курская область",
            federalDistrictId = "ru_central_federal_district",
            lat = 51.7,
            lng = 36.2,
            defaultRadiusKm = 80,
            majorCities = "Kursk, Zheleznogorsk",
            fuelStressStatus = "MEDIUM",
            fiscalStressStatus = "HIGH",
            infrastructureEventCount = 4,
            eventCount = 5,
            lastUpdated = "2026-07-08"
        ),
        RegionEntity(
            regionId = "ru_voronezh_oblast",
            nameEn = "Voronezh Oblast",
            nameUk = "Воронезька область",
            nameRu = "Воронежская область",
            federalDistrictId = "ru_central_federal_district",
            lat = 51.6,
            lng = 39.2,
            defaultRadiusKm = 100,
            majorCities = "Voronezh, Borisoglebsk",
            fuelStressStatus = "LOW",
            fiscalStressStatus = "MEDIUM",
            infrastructureEventCount = 2,
            eventCount = 3,
            lastUpdated = "2026-07-07"
        )
    )

    val maritimeAreas = listOf(
        MaritimeAreaEntity(
            maritimeAreaId = "black_sea_general",
            nameEn = "Black Sea",
            nameUk = "Чорне море",
            theater = "BLACK_SEA",
            lat = 43.5,
            lng = 34.0,
            defaultRadiusKm = 300,
            displayPrecision = "MARITIME_REGIONAL",
            eventCount = 6,
            assetEventsCount = 4,
            sanctionsEventsCount = 2,
            lastUpdated = "2026-07-09"
        ),
        MaritimeAreaEntity(
            maritimeAreaId = "azov_sea_general",
            nameEn = "Azov Sea",
            nameUk = "Азовське море",
            theater = "AZOV_SEA",
            lat = 46.0,
            lng = 37.0,
            defaultRadiusKm = 100,
            displayPrecision = "MARITIME_REGIONAL",
            eventCount = 3,
            assetEventsCount = 2,
            sanctionsEventsCount = 1,
            lastUpdated = "2026-07-08"
        ),
        MaritimeAreaEntity(
            maritimeAreaId = "baltic_sea_general",
            nameEn = "Baltic Sea",
            nameUk = "Балтійське море",
            theater = "BALTIC_SEA",
            lat = 56.0,
            lng = 19.0,
            defaultRadiusKm = 250,
            displayPrecision = "MARITIME_REGIONAL",
            eventCount = 5,
            assetEventsCount = 3,
            sanctionsEventsCount = 4,
            lastUpdated = "2026-07-09"
        ),
        MaritimeAreaEntity(
            maritimeAreaId = "north_sea_general",
            nameEn = "North Sea / Nord Stream Area",
            nameUk = "Північне море / Район Норд Стрім",
            theater = "NORTH_SEA",
            lat = 55.0,
            lng = 6.0,
            defaultRadiusKm = 300,
            displayPrecision = "MARITIME_REGIONAL",
            eventCount = 2,
            assetEventsCount = 1,
            sanctionsEventsCount = 2,
            lastUpdated = "2026-07-05"
        )
    )

    val sanctionsJurisdictions = listOf(
        SanctionsJurisdictionEntity(
            sanctionsJurisdictionId = "eu_general",
            nameEn = "European Union",
            nameUk = "Європейський Союз",
            lat = 50.0,
            lng = 10.0,
            defaultRadiusKm = 500,
            displayPrecision = "SANCTIONS_JURISDICTION"
        ),
        SanctionsJurisdictionEntity(
            sanctionsJurisdictionId = "uk_general",
            nameEn = "United Kingdom",
            nameUk = "Велика Британія",
            lat = 54.0,
            lng = -2.0,
            defaultRadiusKm = 300,
            displayPrecision = "SANCTIONS_JURISDICTION"
        ),
        SanctionsJurisdictionEntity(
            sanctionsJurisdictionId = "us_general",
            nameEn = "United States",
            nameUk = "Сполучені Штати",
            lat = 39.0,
            lng = -77.0,
            defaultRadiusKm = 1000,
            displayPrecision = "SANCTIONS_JURISDICTION"
        )
    )

    val maritimeAssets = listOf(
        MaritimeAssetEntity(
            assetId = "asset_ru_shadow_tanker_001",
            assetName = "Baltic Whisper",
            assetType = "OIL_TANKER",
            assetFlag = "GABON",
            assetRole = "ENERGY_EXPORT",
            knownAliases = "Whisper-1, Sea Runner",
            status = "SANCTIONED",
            statusDate = "2026-05-12",
            sources = "source_lloyds, source_kse"
        ),
        MaritimeAssetEntity(
            assetId = "asset_ru_shadow_tanker_002",
            assetName = "Andromeda Star",
            assetType = "SHADOW_FLEET_TANKER",
            assetFlag = "COOK_ISLANDS",
            assetRole = "ENERGY_EXPORT",
            knownAliases = "Andromeda, Star-X",
            status = "DETAINED",
            statusDate = "2026-06-20",
            sources = "source_lloyds, source_bellingcat"
        ),
        MaritimeAssetEntity(
            assetId = "asset_ru_navy_corvette_001",
            assetName = "Tsiklon",
            assetType = "MISSILE_CORVETTE",
            assetFlag = "RU",
            assetRole = "MILITARY_ASSET",
            knownAliases = "Karakurt-class 633",
            status = "CONFIRMED_DAMAGED",
            statusDate = "2026-05-19",
            sources = "source_rusi, source_bellingcat"
        )
    )

    val events = listOf(
        EventEntity(
            id = "event_20260709_ust_luga_001",
            status = "PUBLISHED",
            titleEn = "Refining Capacity Outage at Ust-Luga Oil Terminal",
            titleUk = "Аварійне відключення потужностей НПЗ на нафтовому терміналі Усть-Луга",
            date = "2026-07-09",
            datePrecision = "DAY",
            category = "ENERGY_EXPORT_DISRUPTION",
            eventScope = "ENERGY_EXPORT",
            theater = "RUSSIA_INTERNAL",
            regionId = "ru_leningrad_oblast",
            federalDistrictId = "ru_northwestern_federal_district",
            maritimeAreaId = null,
            sanctionsJurisdictionId = null,
            approximateLocationLabelEn = "Ust-Luga, Leningrad Oblast",
            approximateLocationLabelUk = "Усть-Луга, Ленінградська область",
            lat = 59.6,
            lng = 28.4,
            radiusKm = 50,
            precision = "REGION_LEVEL",
            assetId = null,
            actor = "NATURAL_OR_ACCIDENT_REPORTED",
            actorConfidence = "INSTITUTIONALLY_REPORTED",
            actorNote = "Attributed to operational failure after continuous technical stress.",
            verificationStatus = "MULTI_SOURCE_CONFIRMED",
            severity = "HIGH",
            summaryEn = "A critical distillation unit at the Ust-Luga Baltic export terminal experienced a sudden automatic shutdown. Satellite infrared monitoring confirmed a dramatic thermal reduction, indicating a complete halt in refining operations. Expected capacity impact is estimated at 150,000 barrels per day for at least 3 weeks.",
            summaryUk = "Критична установка дистиляції на балтійському експортному терміналі Усть-Луга зазнала раптового автоматичного зупинення. Супутниковий інфрачервоний моніторинг підтвердив різке термічне зниження, що вказує на повну зупинку нафтопереробних операцій. Очікуване зниження потужності оцінюється в 150 000 барелів на добу щонайменше на 3 тижні.",
            impactTags = "Refinery, Ust-Luga, Export Stop",
            sources = "source_csis, source_bellingcat",
            safetyNotes = "Location generalized. No exact coordinates. No operational or tactical military details.",
            createdAt = "2026-07-09T12:00:00Z",
            updatedAt = "2026-07-09T14:30:00Z"
        ),
        EventEntity(
            id = "event_20260708_baltic_whisper_sanction",
            status = "PUBLISHED",
            titleEn = "EU Shadow Fleet Sanctions Applied to Tanker Baltic Whisper",
            titleUk = "Санкції ЄС щодо 'тіньового флоту' застосовано до танкера Baltic Whisper",
            date = "2026-07-08",
            datePrecision = "DAY",
            category = "SHADOW_FLEET_SANCTIONS",
            eventScope = "SANCTIONS_ENFORCEMENT",
            theater = "EUROPEAN_WATERS",
            regionId = null,
            federalDistrictId = null,
            maritimeAreaId = "baltic_sea_general",
            sanctionsJurisdictionId = "eu_general",
            approximateLocationLabelEn = "Baltic Sea International Waters",
            approximateLocationLabelUk = "Міжнародні води Балтійського моря",
            lat = 55.8,
            lng = 15.4,
            radiusKm = 250,
            precision = "MARITIME_REGIONAL",
            assetId = "asset_ru_shadow_tanker_001",
            actor = "SANCTIONS_AUTHORITY",
            actorConfidence = "OFFICIALLY_CLAIMED",
            actorNote = "Formal EU Council designation targeting maritime insurance evasion networks.",
            verificationStatus = "OFFICIAL_CONFIRMED",
            severity = "SYSTEMIC",
            summaryEn = "The crude oil carrier Baltic Whisper (IMO 9123456) was officially added to the EU sanctions registry list. The vessel is documented to have engaged in dark-ship transshipments off the coast of Gotland, violating international maritime safety standards and price cap documentation rules. European ports are now prohibited from providing bunkering, tug, and classification services.",
            summaryUk = "Нафтовий танкер Baltic Whisper (IMO 9123456) був офіційно доданий до санкційного реєстру ЄС. Задокументовано, що судно здійснювало перевалку нафти з вимкненими транспондерами біля узбережжя Готланду, порушуючи міжнародні стандарти безпеки мореплавства та правила цінового ліміту. Європейським портам відтепер заборонено надавати бункерування, буксирування та послуги класифікації.",
            impactTags = "Sanctions, Shadow Fleet, Gabon Flag",
            sources = "source_lloyds, source_kse",
            safetyNotes = "Location generalized to regional maritime zone. No exact navigation details.",
            createdAt = "2026-07-08T09:00:00Z",
            updatedAt = "2026-07-08T10:15:00Z"
        ),
        EventEntity(
            id = "event_20260705_novorossiysk_berth_delay",
            status = "PUBLISHED",
            titleEn = "Severe Port Congestion and Loading Delays at Novorossiysk",
            titleUk = "Значне завантаження порту та затримки навантаження в Новоросійську",
            date = "2026-07-05",
            datePrecision = "DAY",
            category = "PORT_LOGISTICS_DISRUPTION",
            eventScope = "LOGISTICS_ROUTE",
            theater = "BLACK_SEA",
            regionId = "ru_krasnodar_krai",
            federalDistrictId = "ru_southern_federal_district",
            maritimeAreaId = "black_sea_general",
            sanctionsJurisdictionId = null,
            approximateLocationLabelEn = "Novorossiysk Maritime District",
            approximateLocationLabelUk = "Морський округ Новоросійська",
            lat = 44.7,
            lng = 37.8,
            radiusKm = 100,
            precision = "MARITIME_REGIONAL",
            assetId = null,
            actor = "NATURAL_OR_ACCIDENT_REPORTED",
            actorConfidence = "MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE",
            actorNote = "Delayed commercial shipping bulletins list extreme anchorage wait times.",
            verificationStatus = "MULTI_SOURCE_CONFIRMED",
            severity = "MEDIUM",
            summaryEn = "AIS tracking logs show commercial oil tanker anchorage waiting times in Novorossiysk have increased from an average of 36 hours to over 9 days. This accumulation is caused by safety restrictions on tug operations and delayed regulatory clearances. Over 18 supertankers are currently waiting in international waters outside the loading zone.",
            summaryUk = "Журнали трекінгу AIS показують, що час очікування комерційних нафтових танкерів на якірній стоянці в Новоросійську зріс із середніх 36 годин до понад 9 днів. Ця затримка спричинена обмеженнями безпеки на буксирні операції та затримками митних погоджень. Понад 18 супертанкерів наразі очікують у міжнародних водах за межами зони навантаження.",
            impactTags = "Port Logistics, Novorossiysk, AIS Log",
            sources = "source_lloyds, source_rusi",
            safetyNotes = "Location generalized. Land coordinates of port are omitted. Representation remains purely maritime.",
            createdAt = "2026-07-05T14:00:00Z",
            updatedAt = "2026-07-05T18:20:00Z"
        ),
        EventEntity(
            id = "event_20260702_rostov_diesel_ration",
            status = "PUBLISHED",
            titleEn = "Regional Agricultural Fuel Allocation Caps in Rostov Oblast",
            titleUk = "Ліміти на розподіл дизельного палива для сільського господарства в Ростовській області",
            date = "2026-07-02",
            datePrecision = "DAY",
            category = "FUEL_SUPPLY_DISRUPTION",
            eventScope = "REGIONAL_INDICATOR",
            theater = "RUSSIA_INTERNAL",
            regionId = "ru_rostov_oblast",
            federalDistrictId = "ru_southern_federal_district",
            maritimeAreaId = null,
            sanctionsJurisdictionId = null,
            approximateLocationLabelEn = "Rostov Oblast Rural Districts",
            approximateLocationLabelUk = "Сільські райони Ростовської області",
            lat = 47.5,
            lng = 40.2,
            radiusKm = 100,
            precision = "REGION_LEVEL",
            assetId = null,
            actor = "LOCAL_GOVERNMENT",
            actorConfidence = "OFFICIALLY_CONFIRMED_BY_TARGET_STATE",
            actorNote = "Rostov regional agricultural ministry published guidelines limiting wholesale distribution.",
            verificationStatus = "ECONOMIC_DATA_CONFIRMED",
            severity = "MEDIUM",
            summaryEn = "Rostov regional authorities have instituted a strict fuel allocation schedule for agricultural enterprises. Wholesale diesel distribution is capped at 75% of previous monthly averages, citing logistics priorities and refinery maintenance in neighboring regions. Local farmers report acute price increases on unregulated secondary fuel markets.",
            summaryUk = "Ростовська регіональна влада запровадила жорсткий графік розподілу палива для сільськогосподарських підприємств. Оптові поставки дизельного палива обмежені 75% від попередніх середньомісячних обсягів через пріоритети військової логістики та технічне обслуговування НПЗ у сусідніх регіонах. Місцеві фермери повідомляють про різке зростання цін на нерегульованому вторинному ринку палива.",
            impactTags = "Agriculture, Rostov, Diesel Caps",
            sources = "source_csis, source_kse",
            safetyNotes = "This is an economic policy record. No military tactical locations are involved.",
            createdAt = "2026-07-02T08:00:00Z",
            updatedAt = "2026-07-03T11:00:00Z"
        ),
        EventEntity(
            id = "event_20260628_flag_deregistration_gabon",
            status = "PUBLISHED",
            titleEn = "Gabon Registry Deregisters Ten Russian Shadow Fleet Vessels",
            titleUk = "Реєстр Габону анулював реєстрацію десяти суден російського 'тіньового флоту'",
            date = "2026-06-28",
            datePrecision = "DAY",
            category = "VESSEL_DEREGISTRATION",
            eventScope = "SANCTIONS_ENFORCEMENT",
            theater = "GLOBAL_SHIPPING",
            regionId = null,
            federalDistrictId = null,
            maritimeAreaId = null,
            sanctionsJurisdictionId = null,
            approximateLocationLabelEn = "Global Shipping Registry Network",
            approximateLocationLabelUk = "Глобальна мережа суднових реєстрів",
            lat = 0.0,
            lng = 0.0,
            radiusKm = 1000,
            precision = "GLOBAL_SHIPPING_GENERAL",
            assetId = null,
            actor = "SANCTIONS_AUTHORITY",
            actorConfidence = "INSTITUTIONALLY_REPORTED",
            actorNote = "International maritime bureau registry bulletin update.",
            verificationStatus = "MULTI_SOURCE_CONFIRMED",
            severity = "HIGH",
            summaryEn = "Following diplomatic pressure from the G7 sanctions coalition, the Maritime Administration of Gabon officially withdrew flag registration from ten crude tankers owned by shadow companies exporting Russian oil. These vessels must secure alternative flags of convenience within 30 days or face port detentions worldwide.",
            summaryUk = "Після дипломатичного тиску з боку санкційної коаліції G7, Морська адміністрація Габону офіційно відкликала реєстрацію прапора у десяти танкерів для сирої нафти, що належать тіньовим компаніям, які експортують російську нафту. Судна мають знайти альтернативні прапори зручності протягом 30 днів, інакше їм загрожує арешт у портах по всьому світу.",
            impactTags = "Flag State, Gabon, Shipping Registers",
            sources = "source_lloyds, source_kse",
            safetyNotes = "Global regulatory event. No combat area locations are described.",
            createdAt = "2026-06-28T16:00:00Z",
            updatedAt = "2026-06-29T09:00:00Z"
        )
    )
}
