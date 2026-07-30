[CmdletBinding()]
param(
    [string]$InputFile,
    [string]$OutputFile,
    [string]$SkipReportFile
)

$ErrorActionPreference = 'Stop'

if (-not $InputFile) {
    $InputFile = Join-Path $PSScriptRoot '..\data\merged\merged_deduped.json'
}

if (-not $OutputFile) {
    $OutputFile = Join-Path $PSScriptRoot '..\app\src\main\assets\osint_events.json'
}

if (-not $SkipReportFile) {
    $SkipReportFile = Join-Path (Split-Path $OutputFile -Parent) 'osint_events_skipped.json'
}

function Get-PropValue {
    param(
        [object]$Record,
        [string[]]$PathCandidates
    )

    foreach ($path in $PathCandidates) {
        $current = $Record
        $found = $true
        foreach ($segment in ($path -split '\.')) {
            if ($null -eq $current -or -not ($current.PSObject.Properties.Name -contains $segment)) {
                $found = $false
                break
            }
            $current = $current.$segment
        }

        if ($found -and $null -ne $current) {
            $value = [string]$current
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                return $value.Trim()
            }
        }
    }

    return $null
}

function Normalize-Key {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ''
    }

    return $Value.Trim().ToLowerInvariant()
}

function New-Point {
    param(
        [double]$Lat,
        [double]$Lng,
        [int]$RadiusKm,
        [object]$RegionId,
        [object]$MaritimeAreaId,
        [string]$Theater,
        [string]$Precision
    )

    [pscustomobject]@{
        lat = $Lat
        lng = $Lng
        radiusKm = $RadiusKm
        regionId = $RegionId
        maritimeAreaId = $MaritimeAreaId
        theater = $Theater
        precision = $Precision
    }
}

$places = @{
    'єйськ' = New-Point 46.71 38.27 80 'ru_krasnodar_krai' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'бердянськ' = New-Point 46.76 36.79 80 $null 'azov_sea_general' 'AZOV_SEA' 'CITY_OR_REGION_ANCHOR'
    'мелітополь' = New-Point 46.85 35.37 90 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'донецьк' = New-Point 48.02 37.80 90 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'луганськ' = New-Point 48.57 39.31 90 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'маріуполь' = New-Point 47.10 37.55 90 $null 'azov_sea_general' 'AZOV_SEA' 'CITY_OR_REGION_ANCHOR'
    'токмак' = New-Point 47.25 35.70 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'нова каховка' = New-Point 46.75 33.37 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'кадіївка' = New-Point 48.57 38.64 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'севастополь' = New-Point 44.62 33.53 90 $null 'black_sea_general' 'BLACK_SEA' 'CITY_OR_REGION_ANCHOR'
    'херсон' = New-Point 46.64 32.62 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'скадовськ' = New-Point 46.12 32.91 80 $null 'black_sea_general' 'BLACK_SEA' 'CITY_OR_REGION_ANCHOR'
    'макіївка' = New-Point 48.05 37.97 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'волноваха' = New-Point 47.60 37.48 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'алчевськ' = New-Point 48.47 38.80 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'шахтарськ' = New-Point 48.05 38.47 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'сватове' = New-Point 49.41 38.15 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'іловайськ' = New-Point 47.92 38.20 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'первомайськ' = New-Point 48.63 38.55 80 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'бєлгород' = New-Point 50.60 36.59 80 'ru_belgorod_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'курськ' = New-Point 51.73 36.19 80 'ru_kursk_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'таганрог' = New-Point 47.24 38.90 80 'ru_rostov_oblast' 'azov_sea_general' 'AZOV_SEA' 'CITY_OR_REGION_ANCHOR'
    'клинці' = New-Point 52.75 32.24 80 'ru_bryansk_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'орел' = New-Point 52.97 36.06 90 'ru_oryol_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'воронеж' = New-Point 51.67 39.21 100 'ru_voronezh_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'глушкове' = New-Point 51.34 34.63 70 'ru_kursk_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'рильськ' = New-Point 51.57 34.68 70 'ru_kursk_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'новошахтинськ' = New-Point 47.76 39.93 80 'ru_rostov_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'ростов-на-дону' = New-Point 47.24 39.70 100 'ru_rostov_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'порт кавказ' = New-Point 45.34 36.68 90 'ru_krasnodar_krai' 'black_sea_general' 'BLACK_SEA' 'MARITIME_REGIONAL'
    'новоросійськ' = New-Point 44.72 37.77 100 'ru_krasnodar_krai' 'black_sea_general' 'BLACK_SEA' 'MARITIME_REGIONAL'
    'керч' = New-Point 45.36 36.47 90 $null 'black_sea_general' 'BLACK_SEA' 'CITY_OR_REGION_ANCHOR'
    'феодосія' = New-Point 45.03 35.38 90 $null 'black_sea_general' 'BLACK_SEA' 'CITY_OR_REGION_ANCHOR'
    'джанкой' = New-Point 45.71 34.39 90 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'сімферополь' = New-Point 44.95 34.10 90 $null $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'ялта' = New-Point 44.50 34.17 90 $null 'black_sea_general' 'BLACK_SEA' 'CITY_OR_REGION_ANCHOR'
    'міллерово' = New-Point 48.92 40.40 80 'ru_rostov_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'морозовськ' = New-Point 48.35 41.83 90 'ru_rostov_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'енгельс' = New-Point 51.48 46.11 100 'ru_saratov_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'рязань' = New-Point 54.63 39.74 100 'ru_ryazan_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'липeцьк' = New-Point 52.61 39.59 100 'ru_lipetsk_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'липецк' = New-Point 52.61 39.59 100 'ru_lipetsk_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'карачев' = New-Point 53.12 34.99 80 'ru_bryansk_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'торопець' = New-Point 56.50 31.64 100 'ru_tver_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'тихорецьк' = New-Point 45.85 40.13 90 'ru_krasnodar_krai' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'котлубань' = New-Point 49.30 44.20 90 'ru_volgograd_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'іваново' = New-Point 57.00 40.97 100 'ru_ivanovo_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'дзержинськ' = New-Point 56.24 43.46 90 'ru_nizhny_novgorod_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'псков' = New-Point 57.82 28.33 100 'ru_pskov_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'сольці' = New-Point 58.12 30.31 90 'ru_novgorod_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'ахтубінськ' = New-Point 48.28 46.17 100 'ru_astrakhan_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'моздок' = New-Point 43.75 44.65 100 'ru_north_ossetia_alania' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'саваслейка' = New-Point 55.45 42.32 90 'ru_nizhny_novgorod_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'кущевська' = New-Point 46.56 39.63 90 'ru_krasnodar_krai' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
}

$regions = @{
    'донецька обл.' = New-Point 48.02 37.80 130 $null $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'запорізька обл.' = New-Point 47.84 35.14 140 $null $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'херсонська обл.' = New-Point 46.64 32.62 140 $null $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'луганська обл.' = New-Point 48.57 39.31 130 $null $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'ар крим' = New-Point 45.20 34.20 150 $null $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'севастополь' = New-Point 44.62 33.53 90 $null 'black_sea_general' 'BLACK_SEA' 'CITY_OR_REGION_ANCHOR'
    'україна' = New-Point 48.38 31.17 450 $null $null 'RUSSIA_INTERNAL' 'COUNTRY_LEVEL'
    'південь україни' = New-Point 46.80 33.90 220 $null $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'схід україни' = New-Point 48.50 37.80 220 $null $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'харківська обл.' = New-Point 49.99 36.23 130 $null $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'миколаївська обл.' = New-Point 47.00 32.00 130 $null 'black_sea_general' 'BLACK_SEA' 'REGION_LEVEL'
    'чорне море' = New-Point 43.50 34.00 300 $null 'black_sea_general' 'BLACK_SEA' 'MARITIME_REGIONAL'
    'рф, бєлгород' = New-Point 50.60 36.59 80 'ru_belgorod_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'рф, бєлгородська обл.' = New-Point 50.70 37.10 110 'ru_belgorod_oblast' $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'рф, курська обл.' = New-Point 51.80 36.00 110 'ru_kursk_oblast' $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'рф, ростовська обл.' = New-Point 47.80 40.50 140 'ru_rostov_oblast' $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'рф, брянська обл.' = New-Point 53.10 33.30 130 'ru_bryansk_oblast' $null 'RUSSIA_INTERNAL' 'REGION_LEVEL'
    'рф, таганрог' = New-Point 47.24 38.90 80 'ru_rostov_oblast' 'azov_sea_general' 'AZOV_SEA' 'CITY_OR_REGION_ANCHOR'
    'рф, курськ' = New-Point 51.73 36.19 80 'ru_kursk_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'рф, воронеж' = New-Point 51.67 39.21 100 'ru_voronezh_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'рф, орел' = New-Point 52.97 36.06 90 'ru_oryol_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'рф, клінці' = New-Point 52.75 32.24 80 'ru_bryansk_oblast' $null 'RUSSIA_INTERNAL' 'CITY_OR_REGION_ANCHOR'
    'рф, порт кавказ' = New-Point 45.34 36.68 90 'ru_krasnodar_krai' 'black_sea_general' 'BLACK_SEA' 'MARITIME_REGIONAL'
}

$maritimeAreas = @{
    'black_sea_general' = New-Point 43.50 34.00 300 $null 'black_sea_general' 'BLACK_SEA' 'MARITIME_REGIONAL'
    'northwestern_black_sea_general' = New-Point 45.80 31.20 150 $null 'black_sea_general' 'BLACK_SEA' 'MARITIME_REGIONAL'
    'ukrainian_coastal_waters_black_sea_general' = New-Point 44.90 33.80 150 $null 'black_sea_general' 'BLACK_SEA' 'MARITIME_REGIONAL'
    'ukrainian_coastal_waters_azov_sea_general' = New-Point 46.40 37.00 120 $null 'azov_sea_general' 'AZOV_SEA' 'MARITIME_REGIONAL'
    'azov_sea_general' = New-Point 46.00 37.00 120 $null 'azov_sea_general' 'AZOV_SEA' 'MARITIME_REGIONAL'
    'baltic_sea_general' = New-Point 56.00 19.00 250 $null 'baltic_sea_general' 'BALTIC_SEA' 'MARITIME_REGIONAL'
    'north_sea_general' = New-Point 55.00 6.00 300 $null 'north_sea_general' 'NORTH_SEA' 'MARITIME_REGIONAL'
}

$maritimeAreaLabels = @{
    'black_sea_general' = @{ en = 'Black Sea'; uk = 'Чорне море' }
    'northwestern_black_sea_general' = @{ en = 'Northwestern Black Sea'; uk = 'Північно-західна частина Чорного моря' }
    'ukrainian_coastal_waters_black_sea_general' = @{ en = 'Ukrainian coastal waters, Black Sea'; uk = 'Українська прибережна акваторія Чорного моря' }
    'ukrainian_coastal_waters_azov_sea_general' = @{ en = 'Ukrainian coastal waters, Azov Sea'; uk = 'Українська прибережна акваторія Азовського моря' }
    'azov_sea_general' = @{ en = 'Azov Sea'; uk = 'Азовське море' }
    'baltic_sea_general' = @{ en = 'Baltic Sea'; uk = 'Балтійське море' }
    'north_sea_general' = @{ en = 'North Sea'; uk = 'Північне море' }
}

function Resolve-Point {
    param([object]$Record)

    $mapPlacement = $Record.mapPlacement
    if ($mapPlacement) {
        $referenceCity = Get-PropValue -Record $mapPlacement -PathCandidates @('referenceCity.uk', 'referenceCity.en')
        if ($referenceCity) {
            foreach ($part in ($referenceCity -split '/|,|—|-')) {
                $key = Normalize-Key $part
                if ($places.ContainsKey($key)) { return $places[$key] }
            }
        }

        $areaId = Get-PropValue -Record $mapPlacement -PathCandidates @('maritimeAreaId')
        if ($areaId -and $maritimeAreas.ContainsKey($areaId)) { return $maritimeAreas[$areaId] }
    }

    $placeRaw = Get-PropValue -Record $Record -PathCandidates @('placeRaw')
    if ($placeRaw) {
        foreach ($part in ($placeRaw -split '/|,|—|-')) {
            $key = Normalize-Key $part
            if ($places.ContainsKey($key)) { return $places[$key] }
        }
    }

    $regionRaw = Get-PropValue -Record $Record -PathCandidates @('regionRaw')
    if ($regionRaw) {
        $key = Normalize-Key $regionRaw
        if ($regions.ContainsKey($key)) { return $regions[$key] }
    }

    $regionId = Get-PropValue -Record $Record -PathCandidates @('regionId')
    if ($regionId) {
        foreach ($regionKey in $regions.Keys) {
            if ($regionId -like "*$($regionKey.Replace(' ', '_'))*") {
                return $regions[$regionKey]
            }
        }
    }

    $maritimeAreaId = Get-PropValue -Record $Record -PathCandidates @('maritimeAreaId')
    if ($maritimeAreaId -and $maritimeAreas.ContainsKey($maritimeAreaId)) {
        return $maritimeAreas[$maritimeAreaId]
    }

    return $null
}

function Get-ArrayText {
    param([object]$Value)

    if ($null -eq $Value) { return '' }
    if ($Value -is [System.Collections.IEnumerable] -and $Value -isnot [string]) {
        return (@($Value) | ForEach-Object { [string]$_ }) -join ', '
    }

    return [string]$Value
}

function Test-IsUkraineOrOccupiedLocation {
    param([object]$Record)

    $text = @(
        (Get-PropValue -Record $Record -PathCandidates @('regionRaw')),
        (Get-PropValue -Record $Record -PathCandidates @('placeRaw')),
        (Get-PropValue -Record $Record -PathCandidates @('mapPlacement.label.uk', 'approximateLocation.label.uk'))
    ) -join ' '

    if ([string]::IsNullOrWhiteSpace($text)) {
        return $false
    }

    return $text -match 'Україн|Україна|Донецьк|Луганськ|Запорізьк|Херсон|Харків|Миколаїв|АР Крим|Крим|Севастополь|Бердянськ|Мелітополь|Маріуполь|Токмак|Кадіївка|Макіївка|Алчевськ|Сватове|Іловайськ|Скадовськ|Нова Каховка|Чорнобаївка'
}

function Test-IsRussiaImportScope {
    param([object]$Record)

    $sourceFile = Get-PropValue -Record $Record -PathCandidates @('_sourceFile')
    $territoryScope = Get-PropValue -Record $Record -PathCandidates @('territoryScope')

    if ($territoryScope -eq 'UKRAINE_OR_OCCUPIED_UKRAINIAN_TERRITORY' -or
        $territoryScope -eq 'UNKNOWN' -or
        $territoryScope -eq 'MARITIME') {
        return $false
    }

    if (Test-IsUkraineOrOccupiedLocation -Record $Record) {
        return $false
    }

    if ($territoryScope -eq 'RUSSIA') {
        return $true
    }

    if ($sourceFile -eq 'baseline_russian_military_bases_depots_airfields_v0_1.json') {
        return $true
    }

    return $false
}

function New-AppEvent {
    param(
        [object]$Record,
        [object]$Point
    )

    $id = Get-PropValue -Record $Record -PathCandidates @('id')
    $date = Get-PropValue -Record $Record -PathCandidates @('date')
    $titleEn = Get-PropValue -Record $Record -PathCandidates @('title.en', '_objectName', 'asset.assetName', 'placeRaw', 'targetSummaryRaw', 'id')
    $titleUk = Get-PropValue -Record $Record -PathCandidates @('title.uk', 'placeRaw', '_objectName', 'targetSummaryRaw', 'id')
    $locationEn = Get-PropValue -Record $Record -PathCandidates @('mapPlacement.label.en', 'approximateLocation.label.en', 'placeRaw', 'regionRaw')
    $locationUk = Get-PropValue -Record $Record -PathCandidates @('mapPlacement.label.uk', 'approximateLocation.label.uk', 'placeRaw', 'regionRaw')
    $summaryUk = Get-PropValue -Record $Record -PathCandidates @('summary.uk', 'targetSummaryRaw', 'outcome', 'placeRaw')
    $summaryEn = Get-PropValue -Record $Record -PathCandidates @('summary.en', 'title.en', '_objectName', 'targetSummaryRaw')
    $createdAt = Get-PropValue -Record $Record -PathCandidates @('createdAt')
    $updatedAt = Get-PropValue -Record $Record -PathCandidates @('updatedAt')
    $category = Get-PropValue -Record $Record -PathCandidates @('category')
    $status = Get-PropValue -Record $Record -PathCandidates @('status')
    $verificationStatus = Get-PropValue -Record $Record -PathCandidates @('verificationStatus')
    $severity = Get-PropValue -Record $Record -PathCandidates @('severity')
    $sourceIds = Get-ArrayText $Record.sourceIds
    if (-not $sourceIds) { $sourceIds = Get-PropValue -Record $Record -PathCandidates @('sourcePageId') }
    if (-not $sourceIds) { $sourceIds = 'source_imported_dataset' }

    $impactTags = Get-ArrayText $Record.impactTags
    if ([string]::IsNullOrWhiteSpace($impactTags)) {
        $tags = @()
        if ($Record.targetCategory) { $tags += [string]$Record.targetCategory }
        if ($Record.weaponsRaw -and [string]$Record.weaponsRaw -ne '—') { $tags += [string]$Record.weaponsRaw }
        if ($Record.outcome) { $tags += [string]$Record.outcome }
        $impactTags = ($tags | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ', '
    }

    if ([string]::IsNullOrWhiteSpace($impactTags)) { $impactTags = $category }
    if ([string]::IsNullOrWhiteSpace($summaryUk)) { $summaryUk = $titleUk }
    if ([string]::IsNullOrWhiteSpace($summaryEn)) { $summaryEn = $titleEn }
    if ([string]::IsNullOrWhiteSpace($locationUk)) { $locationUk = $locationEn }
    if ([string]::IsNullOrWhiteSpace($locationEn)) { $locationEn = $locationUk }
    if ([string]::IsNullOrWhiteSpace($locationEn) -and $Point.maritimeAreaId -and $maritimeAreaLabels.ContainsKey($Point.maritimeAreaId)) {
        $locationEn = $maritimeAreaLabels[$Point.maritimeAreaId].en
    }
    if ([string]::IsNullOrWhiteSpace($locationUk) -and $Point.maritimeAreaId -and $maritimeAreaLabels.ContainsKey($Point.maritimeAreaId)) {
        $locationUk = $maritimeAreaLabels[$Point.maritimeAreaId].uk
    }
    if ([string]::IsNullOrWhiteSpace($locationEn)) { $locationEn = 'Approximate regional location' }
    if ([string]::IsNullOrWhiteSpace($locationUk)) { $locationUk = 'Приблизний регіон' }

    [ordered]@{
        id = $id
        status = if ($status) { $status } else { 'DISCOVERY_DRAFT' }
        titleEn = $titleEn
        titleUk = $titleUk
        date = $date
        datePrecision = if ($Record.datePrecision) { [string]$Record.datePrecision } else { 'DAY' }
        category = if ($category) { $category } else { 'MILITARY_OR_INFRASTRUCTURE_STRIKE_UNCLEAR' }
        eventScope = if ($Record.eventScope) { [string]$Record.eventScope } elseif ($Record.territoryScope -eq 'RUSSIA') { 'TERRITORIAL_RUSSIA' } else { 'MILITARY_ASSET' }
        theater = $Point.theater
        regionId = if ([string]::IsNullOrWhiteSpace([string]$Point.regionId)) { $null } else { $Point.regionId }
        federalDistrictId = Get-PropValue -Record $Record -PathCandidates @('federalDistrictId')
        maritimeAreaId = if ([string]::IsNullOrWhiteSpace([string]$Point.maritimeAreaId)) { $null } else { $Point.maritimeAreaId }
        sanctionsJurisdictionId = Get-PropValue -Record $Record -PathCandidates @('sanctionsJurisdictionId')
        approximateLocationLabelEn = $locationEn
        approximateLocationLabelUk = $locationUk
        lat = [math]::Round([double]$Point.lat, 5)
        lng = [math]::Round([double]$Point.lng, 5)
        radiusKm = [int]$Point.radiusKm
        precision = $Point.precision
        assetId = Get-PropValue -Record $Record -PathCandidates @('asset.assetId')
        actor = if ($Record.actorAttribution -and $Record.actorAttribution.actor) { [string]$Record.actorAttribution.actor } elseif ($Record.actorAttribution) { [string]$Record.actorAttribution } elseif ($Record.attributedActor) { [string]$Record.attributedActor } else { 'UNKNOWN_ACTOR' }
        actorConfidence = 'DISCOVERY_ONLY'
        actorNote = if ($Record.requiresManualReview) { 'Imported discovery record; requires manual verification before production use.' } else { '' }
        verificationStatus = if ($verificationStatus) { $verificationStatus } else { 'MEDIA_REPORTED_WITH_OFFICIAL_REFERENCE' }
        severity = if ($severity) { $severity } else { 'UNKNOWN' }
        summaryEn = $summaryEn
        summaryUk = $summaryUk
        impactTags = $impactTags
        sources = $sourceIds
        safetyNotes = if ($Record.safetyNotes) { [string]$Record.safetyNotes } else { 'Location is approximate and generalized for map display.' }
        createdAt = if ($createdAt) { $createdAt } else { "${date}T00:00:00Z" }
        updatedAt = if ($updatedAt) { $updatedAt } else { "${date}T00:00:00Z" }
    }
}

$input = [System.IO.File]::ReadAllText((Resolve-Path $InputFile).Path, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
$events = New-Object System.Collections.Generic.List[object]
$skipped = New-Object System.Collections.Generic.List[object]

foreach ($record in @($input.records)) {
    if (-not (Test-IsRussiaImportScope -Record $record)) {
        $skipped.Add([pscustomobject]@{
            id = $record.id
            reason = 'outside_russia_scope'
            territoryScope = $record.territoryScope
            placeRaw = $record.placeRaw
            regionRaw = $record.regionRaw
            sourceFile = $record._sourceFile
        }) | Out-Null
        continue
    }

    $date = Get-PropValue -Record $record -PathCandidates @('date')
    if (-not $date -or $date -notmatch '^\d{4}-\d{2}-\d{2}$') {
        $skipped.Add([pscustomobject]@{ id = $record.id; reason = 'invalid_or_missing_date'; date = $date }) | Out-Null
        continue
    }

    $point = Resolve-Point -Record $record
    if (-not $point) {
        $skipped.Add([pscustomobject]@{ id = $record.id; reason = 'missing_mappable_location'; placeRaw = $record.placeRaw; regionRaw = $record.regionRaw }) | Out-Null
        continue
    }

    $events.Add((New-AppEvent -Record $record -Point $point)) | Out-Null
}

$payload = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    sourceFile = (Resolve-Path $InputFile).Path
    importTarget = 'Room EventEntity'
    recordCount = $events.Count
    skippedCount = $skipped.Count
    events = $events
}

New-Item -ItemType Directory -Force -Path (Split-Path $OutputFile -Parent) | Out-Null
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($OutputFile, ($payload | ConvertTo-Json -Depth 100), $utf8NoBom)

New-Item -ItemType Directory -Force -Path (Split-Path $SkipReportFile -Parent) | Out-Null
if ([System.IO.Path]::GetExtension($SkipReportFile) -ieq '.txt') {
    $reportLines = New-Object System.Collections.Generic.List[string]
    $reportLines.Add("Generated at: $($payload.generatedAt)") | Out-Null
    $reportLines.Add("Source file: $((Resolve-Path $InputFile).Path)") | Out-Null
    $reportLines.Add("Records exported: $($events.Count)") | Out-Null
    $reportLines.Add("Records skipped: $($skipped.Count)") | Out-Null
    $reportLines.Add('') | Out-Null
    $reportLines.Add('Skipped reasons:') | Out-Null
    foreach ($group in ($skipped | Group-Object reason | Sort-Object Name)) {
        $reportLines.Add("  $($group.Name): $($group.Count)") | Out-Null
    }
    $reportLines.Add('') | Out-Null
    $reportLines.Add('Sample skipped records:') | Out-Null
    foreach ($item in ($skipped | Select-Object -First 50)) {
        $reportLines.Add("  - $($item.id) :: $($item.reason)") | Out-Null
    }
    $reportLines | Set-Content -Path $SkipReportFile -Encoding utf8
}
else {
    [System.IO.File]::WriteAllText($SkipReportFile, (@{ generatedAt = $payload.generatedAt; skipped = $skipped } | ConvertTo-Json -Depth 20), $utf8NoBom)
}

Write-Output "App events: $OutputFile"
Write-Output "Records exported: $($events.Count)"
Write-Output "Records skipped: $($skipped.Count)"
Write-Output "Skipped report: $SkipReportFile"
