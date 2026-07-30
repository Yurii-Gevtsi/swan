[CmdletBinding()]
param(
    [ValidateSet('2022_2025', '2026')]
    [string]$Year = '2022_2025'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path $PSScriptRoot -Parent
$env:PYTHONIOENCODING = 'utf-8'

& python -u (Join-Path $PSScriptRoot 'import-special-events.py')
if ($LASTEXITCODE -ne 0) {
    throw "Special events import failed with exit code $LASTEXITCODE"
}

& python -u (Join-Path $PSScriptRoot 'import-wikipedia-uav-strikes.py') --year $Year
if ($LASTEXITCODE -ne 0) {
    throw "Wikipedia import failed with exit code $LASTEXITCODE"
}

& python -u (Join-Path $PSScriptRoot 'expand-shadow-fleet-aggregates.py')
if ($LASTEXITCODE -ne 0) {
    throw "Shadow fleet expansion failed with exit code $LASTEXITCODE"
}

& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'finalize-data.ps1')
if ($LASTEXITCODE -ne 0) {
    throw "Data finalization failed with exit code $LASTEXITCODE"
}

if ($Year -eq '2026') {
    & python -u (Join-Path $PSScriptRoot 'build-region-attack-totals.py')
    if ($LASTEXITCODE -ne 0) {
        throw "Region attack totals build failed with exit code $LASTEXITCODE"
    }
}

Write-Output "Wikipedia UAV strike data update completed."
