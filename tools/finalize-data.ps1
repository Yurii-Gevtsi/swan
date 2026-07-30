[CmdletBinding()]
param(
    [switch]$KeepSources,
    [switch]$KeepMergedTemp
)

$ErrorActionPreference = 'Stop'
$env:PYTHONIOENCODING = 'utf-8'
$env:PYTHONUTF8 = '1'

$repoRoot = Split-Path $PSScriptRoot -Parent
$dataRoot = Join-Path $repoRoot 'data'
$mergedDir = Join-Path $dataRoot 'merged'
$finalDir = Join-Path $dataRoot 'final'
$finalDataFile = Join-Path $finalDir 'osint_events.json'
$manualAppAdditionsFile = Join-Path $finalDir 'osint_events_manual_additions.json'
$impactRegistryFile = Join-Path $finalDir 'impact_registry.json'
$manualAdditionsFile = Join-Path $finalDir 'impact_registry_manual_additions.json'
$navalBaselineFile = Join-Path $dataRoot 'res2.json.txt'
$duplicateReportFinal = Join-Path $finalDir 'duplicate_groups.txt'
$impactDuplicateReport = Join-Path $finalDir 'impact_registry_duplicates.txt'
$cleanupReport = Join-Path $finalDir 'cleanup_report.txt'
$appAssetFile = Join-Path $repoRoot 'app\src\main\assets\osint_events.json'
$skipReportFile = Join-Path $finalDir 'osint_events_skipped.txt'
$wikiSnapshotFile = Join-Path $finalDir 'wiki_uav_strikes_2022_2025_snapshot.json'
$wikiSnapshot2026File = Join-Path $finalDir 'wiki_uav_strikes_2026_snapshot.json'
$regionAttackTotalsFile = Join-Path $finalDir 'region_attack_totals_2026.json'
$specialEventsFile = Join-Path $finalDir 'special_events_2022_2025.json'
$mapEventGroupsFile = Join-Path $finalDir 'map_event_groups.json'
$mapEventGroupsReport = Join-Path $finalDir 'map_event_groups_report.txt'
$maritimeCategoryReport = Join-Path $finalDir 'maritime_category_normalization_report.txt'
$appMapEventGroupsFile = Join-Path $repoRoot 'app\src\main\assets\map_event_groups.json'
$citationSourcesFile = Join-Path $finalDir 'wikipedia_citation_sources.json'

New-Item -ItemType Directory -Force -Path $finalDir | Out-Null

if (Test-Path $appAssetFile) {
    New-Item -ItemType Directory -Force -Path (Split-Path $appAssetFile -Parent) | Out-Null
}

$rawSourceFiles = Get-ChildItem -Path $dataRoot -Filter '*.json' -File
$exportPayload = $null

if ($rawSourceFiles.Count -gt 0) {
    Write-Output 'Running data merge...'
    & (Join-Path $PSScriptRoot 'merge-data.ps1') -InputDirectory $dataRoot -OutputDirectory $mergedDir

    $mergedFile = Join-Path $mergedDir 'merged_deduped.json'
    $duplicateReportSource = Join-Path $mergedDir 'duplicate_groups.txt'
    if (-not (Test-Path $mergedFile)) {
        throw "Merged file was not created: $mergedFile"
    }

    Write-Output 'Exporting app-ready events...'
    & (Join-Path $PSScriptRoot 'export-app-events.ps1') -InputFile $mergedFile -OutputFile $appAssetFile -SkipReportFile $skipReportFile

    $exportPayload = Get-Content -Path $appAssetFile -Raw | ConvertFrom-Json
    if (-not $exportPayload -or $exportPayload.recordCount -le 0) {
        throw 'Final export produced no events, aborting cleanup.'
    }

    Copy-Item -LiteralPath $appAssetFile -Destination $finalDataFile -Force
    if (Test-Path $duplicateReportSource) {
        Copy-Item -LiteralPath $duplicateReportSource -Destination $duplicateReportFinal -Force
    }
}
elseif (Test-Path $finalDataFile) {
    Write-Output 'No raw source JSON files found. Reusing the existing final dataset.'
    Copy-Item -LiteralPath $finalDataFile -Destination $appAssetFile -Force
    $exportPayload = Get-Content -Path $finalDataFile -Raw | ConvertFrom-Json
}
else {
    throw "No raw source JSON files were found in $dataRoot and no final dataset exists at $finalDataFile"
}

if (-not $exportPayload -or $exportPayload.recordCount -le 0) {
    throw 'Final dataset is empty, aborting cleanup.'
}

Write-Output 'Refreshing special operations register...'
& python -u (Join-Path $PSScriptRoot 'import-special-events.py')
if ($LASTEXITCODE -ne 0) {
    throw "Special operations import failed with exit code $LASTEXITCODE"
}

Write-Output 'Refreshing naval vessel map events...'
& python -u (Join-Path $PSScriptRoot 'import-naval-registry-events.py')
if ($LASTEXITCODE -ne 0) {
    throw "Naval vessel event import failed with exit code $LASTEXITCODE"
}

if (Test-Path $manualAppAdditionsFile) {
    Write-Output 'Applying manual app event additions...'

    Write-Output 'Localizing manual addition English text...'
    & python -u (Join-Path $PSScriptRoot 'localize-event-names.py') `
        --input $manualAppAdditionsFile `
        --app-output $manualAppAdditionsFile
    if ($LASTEXITCODE -ne 0) {
        throw "Manual addition localization failed with exit code $LASTEXITCODE"
    }

    # Wikipedia tables are mutable, so replace their generated event subset
    # instead of only upserting it. This prevents removed or edited rows from
    # surviving forever under an older generated ID.
    $basePayload = Get-Content -Path $finalDataFile -Raw | ConvertFrom-Json
    $manualPayload = Get-Content -Path $manualAppAdditionsFile -Raw | ConvertFrom-Json
    $currentWikipediaEvents = @($manualPayload.events | Where-Object { $_.id -match '_wiki_uav_strike_' })
    if ($currentWikipediaEvents.Count -gt 0) {
        $basePayload.events = @($basePayload.events | Where-Object { $_.id -notmatch '_wiki_uav_strike_' })
        $basePayload.recordCount = $basePayload.events.Count
        $basePayload | ConvertTo-Json -Depth 30 | Set-Content -Path $finalDataFile -Encoding utf8
        Write-Output "Removed stale Wikipedia-generated events before replacement: $($currentWikipediaEvents.Count) current records"
    }
    $currentNavalEvents = @($manualPayload.events | Where-Object { $_.id -match '^event_naval_registry_' })
    if ($currentNavalEvents.Count -gt 0) {
        $basePayload = Get-Content -Path $finalDataFile -Raw | ConvertFrom-Json
        $basePayload.events = @($basePayload.events | Where-Object { $_.id -notmatch '^event_naval_registry_' })
        $basePayload.recordCount = $basePayload.events.Count
        $basePayload | ConvertTo-Json -Depth 30 | Set-Content -Path $finalDataFile -Encoding utf8
        Write-Output "Removed stale naval registry events before replacement: $($currentNavalEvents.Count) current records"
    }

    & (Join-Path $PSScriptRoot 'merge-event-snapshots.ps1') `
        -BaseFile $finalDataFile `
        -SupplementFiles $manualAppAdditionsFile `
        -OutputFile $finalDataFile

    Copy-Item -LiteralPath $finalDataFile -Destination $appAssetFile -Force
    $exportPayload = Get-Content -Path $finalDataFile -Raw | ConvertFrom-Json
}

Write-Output 'Repairing known corrupted legacy event records...'
& python -u (Join-Path $PSScriptRoot 'repair-corrupted-event-records.py') `
    --input $finalDataFile `
    --app-output $appAssetFile
if ($LASTEXITCODE -ne 0) {
    throw "Legacy event text repair failed with exit code $LASTEXITCODE"
}

Write-Output 'Localizing English event and object names...'
& python -u (Join-Path $PSScriptRoot 'localize-event-names.py') `
    --input $finalDataFile `
    --app-output $appAssetFile
if ($LASTEXITCODE -ne 0) {
    throw "Event name localization failed with exit code $LASTEXITCODE"
}

Write-Output 'Normalizing verification status levels...'
& python -u (Join-Path $PSScriptRoot 'normalize-verification-status.py') `
    --input $finalDataFile `
    --app-output $appAssetFile
if ($LASTEXITCODE -ne 0) {
    throw "Verification status normalization failed with exit code $LASTEXITCODE"
}

Write-Output 'Separating civilian vessels from the naval fleet...'
& python -u (Join-Path $PSScriptRoot 'normalize-maritime-fleet-categories.py') `
    --input $finalDataFile `
    --app-output $appAssetFile `
    --report $maritimeCategoryReport
if ($LASTEXITCODE -ne 0) {
    throw "Maritime category normalization failed with exit code $LASTEXITCODE"
}

Write-Output 'Canonicalizing categories to the six map filters...'
& python -u (Join-Path $PSScriptRoot 'canonicalize-categories.py') `
    --input $finalDataFile `
    --app-output $appAssetFile `
    --report (Join-Path $finalDir 'category_canonicalization_report.txt')
if ($LASTEXITCODE -ne 0) {
    throw "Category canonicalization failed with exit code $LASTEXITCODE"
}

Write-Output 'Scattering piled approximate coordinates and normalizing radii...'
& python -u (Join-Path $PSScriptRoot 'scatter-approximate-events.py') `
    --input $finalDataFile `
    --app-output $appAssetFile
if ($LASTEXITCODE -ne 0) {
    throw "Coordinate scattering failed with exit code $LASTEXITCODE"
}

Write-Output 'Enforcing Russia-only rule (excluding occupied Ukrainian territory)...'
& python -u (Join-Path $PSScriptRoot 'enforce-russia-only.py') `
    --input $finalDataFile `
    --app-output $appAssetFile `
    --report (Join-Path $finalDir 'russia_only_report.txt')
if ($LASTEXITCODE -ne 0) {
    throw "Russia-only enforcement failed with exit code $LASTEXITCODE"
}

Write-Output 'Building map object groups...'
& python -u (Join-Path $PSScriptRoot 'build-map-event-groups.py') `
    --input $finalDataFile `
    --output $mapEventGroupsFile `
    --app-output $appMapEventGroupsFile `
    --report $mapEventGroupsReport
if ($LASTEXITCODE -ne 0) {
    throw "Map event grouping failed with exit code $LASTEXITCODE"
}

Write-Output 'Repairing generated map group titles...'
& python -u (Join-Path $PSScriptRoot 'repair-map-group-titles.py') `
    --events $finalDataFile `
    --input $mapEventGroupsFile `
    --app-output $appMapEventGroupsFile
if ($LASTEXITCODE -ne 0) {
    throw "Map group title repair failed with exit code $LASTEXITCODE"
}

Write-Output 'Repairing Wikipedia snapshot English labels...'
& python -u (Join-Path $PSScriptRoot 'repair-wiki-snapshot-labels.py')
if ($LASTEXITCODE -ne 0) {
    throw "Wikipedia snapshot label repair failed with exit code $LASTEXITCODE"
}

Write-Output 'Updating compact impact registry...'
$registryInputs = New-Object System.Collections.Generic.List[string]
if (Test-Path $finalDataFile) {
    $registryInputs.Add($finalDataFile) | Out-Null
}
if (Test-Path $navalBaselineFile) {
    $registryInputs.Add($navalBaselineFile) | Out-Null
}
if (Test-Path $manualAdditionsFile) {
    $registryInputs.Add($manualAdditionsFile) | Out-Null
}

& (Join-Path $PSScriptRoot 'update-impact-registry.ps1') -InputFiles $registryInputs.ToArray() -RegistryFile $impactRegistryFile -DuplicateReportFile $impactDuplicateReport -ResetRegistry

Write-Output 'Validating localization and UTF-8 integrity...'
& python -u (Join-Path $PSScriptRoot 'validate-localization.py') --repo-root $repoRoot
if ($LASTEXITCODE -ne 0) {
    throw "Localization validation failed with exit code $LASTEXITCODE"
}

Write-Output 'Validating map event coverage...'
& python -u (Join-Path $PSScriptRoot 'validate-map-coverage.py') --repo-root $repoRoot
if ($LASTEXITCODE -ne 0) {
    throw "Map coverage validation failed with exit code $LASTEXITCODE"
}

$deletedJsonFiles = New-Object System.Collections.Generic.List[string]
if (-not $KeepSources) {
    $protected = @(
        (Resolve-Path $finalDataFile).Path,
        (Resolve-Path $impactRegistryFile).Path
    )
    if (Test-Path $manualAppAdditionsFile) {
        $protected += (Resolve-Path $manualAppAdditionsFile).Path
    }
    if (Test-Path $manualAdditionsFile) {
        $protected += (Resolve-Path $manualAdditionsFile).Path
    }
    if (Test-Path $wikiSnapshotFile) {
        $protected += (Resolve-Path $wikiSnapshotFile).Path
    }
    if (Test-Path $wikiSnapshot2026File) {
        $protected += (Resolve-Path $wikiSnapshot2026File).Path
    }
    if (Test-Path $regionAttackTotalsFile) {
        $protected += (Resolve-Path $regionAttackTotalsFile).Path
    }
    if (Test-Path $specialEventsFile) {
        $protected += (Resolve-Path $specialEventsFile).Path
    }
    if (Test-Path $mapEventGroupsFile) {
        $protected += (Resolve-Path $mapEventGroupsFile).Path
    }
    if (Test-Path $citationSourcesFile) {
        $protected += (Resolve-Path $citationSourcesFile).Path
    }
    $jsonFiles = Get-ChildItem -Path $dataRoot -Recurse -Filter '*.json' -File
    foreach ($file in $jsonFiles) {
        if ($protected -contains $file.FullName) {
            continue
        }

        Remove-Item -LiteralPath $file.FullName -Force
        $deletedJsonFiles.Add($file.FullName) | Out-Null
    }

    $legacySkipReport = Join-Path $repoRoot 'app\src\main\assets\osint_events_skipped.json'
    if (Test-Path $legacySkipReport) {
        Remove-Item -LiteralPath $legacySkipReport -Force
    }
}

if (-not $KeepMergedTemp) {
    if (Test-Path $mergedDir) {
        Get-ChildItem -Path $mergedDir -Force -ErrorAction SilentlyContinue | Remove-Item -Force -Recurse
        if (-not (Get-ChildItem -Path $mergedDir -Force -ErrorAction SilentlyContinue)) {
            Remove-Item -LiteralPath $mergedDir -Force
        }
    }
}

$cleanupLines = New-Object System.Collections.Generic.List[string]
$cleanupLines.Add("Generated at: $((Get-Date).ToUniversalTime().ToString('o'))") | Out-Null
$cleanupLines.Add("Final data file: $finalDataFile") | Out-Null
$cleanupLines.Add("Manual app additions file: $manualAppAdditionsFile") | Out-Null
$cleanupLines.Add("Impact registry file: $impactRegistryFile") | Out-Null
$cleanupLines.Add("Manual additions file: $manualAdditionsFile") | Out-Null
$cleanupLines.Add("Naval baseline file: $navalBaselineFile") | Out-Null
$cleanupLines.Add("Wikipedia UAV snapshot: $wikiSnapshotFile") | Out-Null
$cleanupLines.Add("Wikipedia UAV 2026 snapshot: $wikiSnapshot2026File") | Out-Null
$cleanupLines.Add("Region attack totals: $regionAttackTotalsFile") | Out-Null
$cleanupLines.Add("Special narrative events: $specialEventsFile") | Out-Null
$cleanupLines.Add("Wikipedia citation sources: $citationSourcesFile") | Out-Null
$cleanupLines.Add("App asset file: $appAssetFile") | Out-Null
$cleanupLines.Add("Events exported: $($exportPayload.recordCount)") | Out-Null
$cleanupLines.Add("Skipped records: $($exportPayload.skippedCount)") | Out-Null
$cleanupLines.Add("Deleted JSON files: $($deletedJsonFiles.Count)") | Out-Null
if ($deletedJsonFiles.Count -gt 0) {
    $cleanupLines.Add('') | Out-Null
    $cleanupLines.Add('Deleted paths:') | Out-Null
    foreach ($path in $deletedJsonFiles) {
        $cleanupLines.Add("  - $path") | Out-Null
    }
}
$cleanupLines | Set-Content -Path $cleanupReport -Encoding utf8

Write-Output "Final data file: $finalDataFile"
Write-Output "Impact registry: $impactRegistryFile"
Write-Output "Cleanup report: $cleanupReport"
Write-Output "Duplicate report: $duplicateReportFinal"
Write-Output "Impact duplicate report: $impactDuplicateReport"
Write-Output "Events exported: $($exportPayload.recordCount)"
Write-Output "Deleted JSON files: $($deletedJsonFiles.Count)"
