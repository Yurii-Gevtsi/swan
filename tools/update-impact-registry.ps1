[CmdletBinding()]
param(
    [string[]]$InputFiles,
    [string]$RegistryFile,
    [string]$DuplicateReportFile,
    [switch]$ResetRegistry
)

$ErrorActionPreference = 'Stop'

if (-not $InputFiles -or $InputFiles.Count -eq 0) {
    $InputFiles = @((Join-Path $PSScriptRoot '..\data\final\osint_events.json'))
}

if (-not $RegistryFile) {
    $RegistryFile = Join-Path $PSScriptRoot '..\data\final\impact_registry.json'
}

if (-not $DuplicateReportFile) {
    $DuplicateReportFile = Join-Path $PSScriptRoot '..\data\final\impact_registry_duplicates.txt'
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

function Normalize-Whitespace {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $null
    }

    return (($Value -replace '\s+', ' ').Trim())
}

function Normalize-KeyPart {
    param([string]$Value)

    $normalized = Normalize-Whitespace -Value $Value
    if (-not $normalized) {
        return ''
    }

    return ($normalized.ToLowerInvariant() -replace '[^0-9\p{L}]+', ' ').Trim()
}

function Get-CompactObjectName {
    param([object]$Record)

    $directName = Get-PropValue -Record $Record -PathCandidates @(
        'objectName',
        'asset.assetName',
        '_objectName',
        'targetSummaryRaw',
        'title.uk',
        'title.en'
    )
    if ($directName) {
        return Normalize-Whitespace -Value $directName
    }

    $titleEn = Get-PropValue -Record $Record -PathCandidates @('titleEn')
    if ($titleEn) {
        $candidate = $titleEn `
            -replace '^event\s+\d{8}\s+', '' `
            -replace '\s+\d{3,}$', '' `
            -replace '\b(damage|strike|hit|attack|incident|fire|loss|disruption)\b', '' `
            -replace '_', ' '
        $candidate = Normalize-Whitespace -Value $candidate
        if ($candidate) {
            return $candidate
        }
    }

    $fallback = Get-PropValue -Record $Record -PathCandidates @(
        'targetSummaryRaw',
        'titleUk',
        'titleEn',
        'placeRaw',
        'id'
    )
    if ($fallback) {
        return Normalize-Whitespace -Value $fallback
    }

    return 'unknown object'
}

function Get-StrikeLocation {
    param([object]$Record)

    $location = Get-PropValue -Record $Record -PathCandidates @(
        'strikeLocation',
        'approximateLocationLabelUk',
        'mapPlacement.label.uk',
        'approximateLocation.label.uk',
        'placeRaw',
        'regionRaw',
        'approximateLocationLabelEn',
        'mapPlacement.label.en'
    )

    if ($location) {
        return Normalize-Whitespace -Value $location
    }

    return 'unknown location'
}

function Get-StrikeDate {
    param([object]$Record)

    $date = Get-PropValue -Record $Record -PathCandidates @('strikeDate', 'date')
    if ($date) {
        return $date
    }

    return 'unknown-date'
}

function Get-Category {
    param([object]$Record)

    $category = Get-PropValue -Record $Record -PathCandidates @('category')
    if ($category) {
        return Normalize-Whitespace -Value $category
    }

    return 'UNCATEGORIZED'
}

function Convert-ToRegistryItem {
    param(
        [object]$Record,
        [string]$SourceFile,
        [string]$SourceArray
    )

    $objectName = Get-CompactObjectName -Record $Record
    $strikeLocation = Get-StrikeLocation -Record $Record
    $strikeDate = Get-StrikeDate -Record $Record
    $category = Get-Category -Record $Record

    [pscustomobject]@{
        objectName = $objectName
        strikeLocation = $strikeLocation
        strikeDate = $strikeDate
        category = $category
        dedupeKey = ('{0}||{1}' -f (Normalize-KeyPart $objectName), (Normalize-KeyPart $strikeDate))
        sourceFile = $SourceFile
        sourceArray = $SourceArray
    }
}

function Get-SourceItems {
    param([string]$Path)

    $resolvedPath = (Resolve-Path $Path).Path
    $root = [System.IO.File]::ReadAllText($resolvedPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
    $items = @()

    if ($root.PSObject.Properties.Name -contains 'events') {
        $items = @($root.events)
    }
    elseif ($root.PSObject.Properties.Name -contains 'records') {
        $items = @($root.records)
    }
    elseif ($root.PSObject.Properties.Name -contains 'items') {
        $items = @($root.items)
    }
    else {
        throw "Unsupported input shape in $resolvedPath"
    }

    $maritimeAreaLookup = @{}
    if (($root.PSObject.Properties.Name -contains 'maritimeAreaRegistry') -and $root.maritimeAreaRegistry) {
        foreach ($area in @($root.maritimeAreaRegistry)) {
            $areaId = [string]$area.id
            if ([string]::IsNullOrWhiteSpace($areaId)) {
                continue
            }

            $areaName = $null
            if ($area.PSObject.Properties.Name -contains 'name') {
                if ($area.name.PSObject.Properties.Name -contains 'en') {
                    $areaName = Normalize-Whitespace -Value ([string]$area.name.en)
                }

                if (-not $areaName -and ($area.name.PSObject.Properties.Name -contains 'uk')) {
                    $areaName = Normalize-Whitespace -Value ([string]$area.name.uk)
                }
            }

            if ($areaName) {
                $maritimeAreaLookup[$areaId] = $areaName
            }
        }
    }

    foreach ($item in $items) {
        if (-not $maritimeAreaLookup.Count) {
            continue
        }

        $currentLocation = Get-PropValue -Record $item -PathCandidates @('strikeLocation')
        if ($currentLocation) {
            continue
        }

        $areaId = Get-PropValue -Record $item -PathCandidates @('maritimeAreaId')
        if ($areaId -and $maritimeAreaLookup.ContainsKey($areaId)) {
            Add-Member -InputObject $item -NotePropertyName 'strikeLocation' -NotePropertyValue $maritimeAreaLookup[$areaId] -Force
        }
    }

    if ($root.PSObject.Properties.Name -contains 'events') {
        return @{
            SourceArray = 'events'
            Items = $items
        }
    }

    if ($root.PSObject.Properties.Name -contains 'records') {
        return @{
            SourceArray = 'records'
            Items = $items
        }
    }

    return @{
        SourceArray = 'items'
        Items = $items
    }
}

New-Item -ItemType Directory -Force -Path (Split-Path $RegistryFile -Parent) | Out-Null
New-Item -ItemType Directory -Force -Path (Split-Path $DuplicateReportFile -Parent) | Out-Null

$seen = @{}
$registryItems = New-Object System.Collections.Generic.List[object]
$duplicates = New-Object System.Collections.Generic.List[object]

if ((-not $ResetRegistry) -and (Test-Path $RegistryFile)) {
    $existingRoot = [System.IO.File]::ReadAllText((Resolve-Path $RegistryFile).Path, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
    foreach ($item in @($existingRoot.items)) {
        $key = '{0}||{1}' -f (Normalize-KeyPart $item.objectName), (Normalize-KeyPart $item.strikeDate)
        if (-not $seen.ContainsKey($key)) {
            $seen[$key] = $true
            $registryItems.Add([pscustomobject]@{
                objectName = (Normalize-Whitespace -Value ([string]$item.objectName))
                strikeLocation = (Normalize-Whitespace -Value ([string]$item.strikeLocation))
                strikeDate = [string]$item.strikeDate
                category = (Normalize-Whitespace -Value ([string]$item.category))
                dedupeKey = $key
                sourceFile = 'impact_registry.json'
                sourceArray = 'items'
            }) | Out-Null
        }
    }
}

foreach ($inputFile in $InputFiles) {
    $source = Get-SourceItems -Path $inputFile
    $sourceFileName = Split-Path $inputFile -Leaf

    foreach ($record in $source.Items) {
        $item = Convert-ToRegistryItem -Record $record -SourceFile $sourceFileName -SourceArray $source.SourceArray

        if (-not $item.objectName -or -not $item.strikeDate) {
            continue
        }

        if ($seen.ContainsKey($item.dedupeKey)) {
            $duplicates.Add($item) | Out-Null
            continue
        }

        $seen[$item.dedupeKey] = $true
        $registryItems.Add($item) | Out-Null
    }
}

$sortedItems = @(
    $registryItems |
        Sort-Object @{ Expression = { $_.strikeDate }; Descending = $true }, @{ Expression = { $_.objectName }; Descending = $false } |
        ForEach-Object {
            [pscustomobject]@{
                objectName = $_.objectName
                strikeLocation = $_.strikeLocation
                strikeDate = $_.strikeDate
                category = $_.category
            }
        }
)

$payload = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    dedupeStrategy = 'normalized object name + strike date'
    recordCount = $sortedItems.Count
    duplicateCount = $duplicates.Count
    items = $sortedItems
}

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add("Generated at: $($payload.generatedAt)") | Out-Null
$reportLines.Add("Registry file: $((Resolve-Path (Split-Path $RegistryFile -Parent)).Path)\$(Split-Path $RegistryFile -Leaf)") | Out-Null
$reportLines.Add("Record count: $($sortedItems.Count)") | Out-Null
$reportLines.Add("Duplicate count: $($duplicates.Count)") | Out-Null
$reportLines.Add('') | Out-Null

if ($duplicates.Count -eq 0) {
    $reportLines.Add('No duplicates found during this update.') | Out-Null
}
else {
    foreach ($item in $duplicates) {
        $reportLines.Add("$($item.strikeDate) :: $($item.objectName) :: $($item.strikeLocation) :: $($item.category) :: $($item.sourceFile)") | Out-Null
    }
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($RegistryFile, ($payload | ConvertTo-Json -Depth 20), $utf8NoBom)
$reportLines | Set-Content -Path $DuplicateReportFile -Encoding utf8

Write-Output "Impact registry: $RegistryFile"
Write-Output "Registry records: $($sortedItems.Count)"
Write-Output "Duplicates skipped: $($duplicates.Count)"
Write-Output "Duplicate report: $DuplicateReportFile"
