[CmdletBinding()]
param(
    # Weekly input JSON (default: the newest data/weekly/*.json).
    [string]$InputFile,
    # Skip the weekly import step (just rebuild + publish current data).
    [switch]$SkipImport,
    # Skip finalize-data.ps1 (publish the current app assets as-is).
    [switch]$SkipFinalize,
    # Build everything but do not run firebase deploy.
    [switch]$SkipDeploy
)

$ErrorActionPreference = 'Stop'
$env:PYTHONIOENCODING = 'utf-8'
$env:PYTHONUTF8 = '1'

$repoRoot = Split-Path $PSScriptRoot -Parent
$weeklyDir = Join-Path $repoRoot 'data\weekly'
$processedDir = Join-Path $weeklyDir 'processed'
$assetsDir = Join-Path $repoRoot 'app\src\main\assets'
$siteDataDir = Join-Path $repoRoot 'site\data'

# The JSON files the app downloads from hosting (same names as bundled assets).
$publishedFiles = @(
    'osint_events.json',
    'map_event_groups.json',
    'region_attack_totals_2026.json',
    'fuel_shortage_regions_2026.json',
    'regional_budget_stress_2026.json',
    'wikipedia_citation_sources.json'
)

# 1. Import the weekly raw input into the manual-additions snapshot.
if (-not $SkipImport) {
    if (-not $InputFile) {
        New-Item -ItemType Directory -Force -Path $weeklyDir | Out-Null
        $candidate = Get-ChildItem -Path $weeklyDir -Filter '*.json' -File |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if (-not $candidate) {
            throw "No weekly input found in $weeklyDir. Pass -InputFile or use -SkipImport."
        }
        $InputFile = $candidate.FullName
    }
    Write-Output "Importing weekly input: $InputFile"
    & python -u (Join-Path $PSScriptRoot 'import-weekly-input.py') --input $InputFile
    if ($LASTEXITCODE -ne 0) {
        throw "Weekly import reported errors (exit code $LASTEXITCODE). Fix the input and retry."
    }

    # Archive immediately (as .txt): finalize-data.ps1 cleanup deletes any stray
    # *.json under data/, which would silently eat the input file otherwise.
    New-Item -ItemType Directory -Force -Path $processedDir | Out-Null
    $stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd_HHmmss')
    $archiveName = "{0}_{1}.txt" -f $stamp, (Split-Path $InputFile -Leaf)
    Move-Item -LiteralPath $InputFile -Destination (Join-Path $processedDir $archiveName) -Force
    Write-Output "Archived weekly input to data\weekly\processed\$archiveName"
}

# 2. Rebuild the full dataset (dedupe, localization, map groups, app assets).
if ($SkipFinalize) {
    Write-Output 'SkipFinalize set: publishing current app assets as-is.'
}
else {
    Write-Output 'Running finalize-data.ps1...'
    & (Join-Path $PSScriptRoot 'finalize-data.ps1')
}

# 3. Copy app-ready JSON files to the hosting folder.
New-Item -ItemType Directory -Force -Path $siteDataDir | Out-Null

# Remember the previously published event count: the validator refuses to
# publish a package that suddenly shrank (guards against truncated data).
$previousEventCount = 0
$previousManifestPath = Join-Path $siteDataDir 'manifest.json'
if (Test-Path $previousManifestPath) {
    try {
        $previousManifest = Get-Content $previousManifestPath -Raw | ConvertFrom-Json
        if ($previousManifest.eventCount) { $previousEventCount = [int]$previousManifest.eventCount }
    } catch { }
}

foreach ($name in $publishedFiles) {
    $sourcePath = Join-Path $assetsDir $name
    if (-not (Test-Path $sourcePath)) {
        throw "Expected app asset is missing: $sourcePath"
    }
    Copy-Item -LiteralPath $sourcePath -Destination (Join-Path $siteDataDir $name) -Force
}

# 4. Write the manifest the app polls for updates.
$eventCount = (Get-Content (Join-Path $siteDataDir 'osint_events.json') -Raw | ConvertFrom-Json).recordCount
$nowUtc = (Get-Date).ToUniversalTime()
$manifest = [ordered]@{
    schemaVersion = 1
    dataVersion = $nowUtc.ToString('yyyyMMddTHHmmssZ')
    generatedAt = $nowUtc.ToString('o')
    eventCount = $eventCount
    files = $publishedFiles
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText(
    (Join-Path $siteDataDir 'manifest.json'),
    (($manifest | ConvertTo-Json -Depth 5) + "`n"),
    $utf8NoBom
)
Write-Output "Manifest dataVersion: $($manifest.dataVersion) (events: $eventCount)"

# 5. Final package validation - refuses to deploy a broken or shrunken package.
$minEvents = [int]([math]::Floor($previousEventCount * 0.9))
Write-Output 'Validating localization and translation integrity...'
& python -u (Join-Path $PSScriptRoot 'validate-localization.py') --repo-root $repoRoot --include-site-data
if ($LASTEXITCODE -ne 0) {
    throw 'Localization validation FAILED - deploy aborted. Fix the reported issues and rerun.'
}
Write-Output "Validating package (previous publish had $previousEventCount events)..."
& python -u (Join-Path $PSScriptRoot 'validate-data-package.py') --site-data $siteDataDir --min-events $minEvents
if ($LASTEXITCODE -ne 0) {
    throw 'Package validation FAILED - deploy aborted. Fix the reported errors and rerun.'
}

# 6. Deploy to Firebase Hosting.
if ($SkipDeploy) {
    Write-Output 'SkipDeploy set: run "firebase deploy --only hosting" when ready.'
}
else {
    where.exe firebase.cmd *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Firebase CLI is not installed. Run: npm.cmd install --global firebase-tools'
    }
    firebase.cmd deploy --only hosting
    if ($LASTEXITCODE -ne 0) {
        throw "firebase deploy failed with exit code $LASTEXITCODE"
    }
    Write-Output 'Published. The app will pick up the new dataVersion on its next sync.'
}
