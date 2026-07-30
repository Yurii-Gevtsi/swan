[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseFile,
    [string[]]$SupplementFiles,
    [Parameter(Mandatory = $true)]
    [string]$OutputFile
)

$ErrorActionPreference = 'Stop'

function Read-Snapshot {
    param([string]$Path)

    $resolvedPath = (Resolve-Path $Path).Path
    return [System.IO.File]::ReadAllText($resolvedPath, [System.Text.Encoding]::UTF8) | ConvertFrom-Json
}

function Normalize-Date {
    param([object]$Value)

    $text = [string]$Value
    if ([string]::IsNullOrWhiteSpace($text)) {
        return '0000-00-00'
    }

    return $text
}

$baseRoot = Read-Snapshot -Path $BaseFile
if (-not ($baseRoot.PSObject.Properties.Name -contains 'events')) {
    throw "Unsupported base snapshot shape in $BaseFile"
}

$eventsById = @{}
$eventPriorityById = @{}

foreach ($event in @($baseRoot.events)) {
    $eventId = [string]$event.id
    if ([string]::IsNullOrWhiteSpace($eventId)) {
        continue
    }

    $eventsById[$eventId] = $event
    $eventPriorityById[$eventId] = 0
}

$supplementPriority = 0
foreach ($supplementFile in @($SupplementFiles)) {
    if (-not (Test-Path $supplementFile)) {
        continue
    }

    $supplementRoot = Read-Snapshot -Path $supplementFile
    if (-not ($supplementRoot.PSObject.Properties.Name -contains 'events')) {
        throw "Unsupported supplement snapshot shape in $supplementFile"
    }

    $supplementPriority++
    foreach ($event in @($supplementRoot.events)) {
        $eventId = [string]$event.id
        if ([string]::IsNullOrWhiteSpace($eventId)) {
            continue
        }

        $eventsById[$eventId] = $event
        $eventPriorityById[$eventId] = $supplementPriority
    }
}

$sortedEventEntries = @(
    $eventsById.GetEnumerator() |
        ForEach-Object {
            [pscustomobject]@{
                Event = $_.Value
                Priority = [int]$eventPriorityById[[string]$_.Key]
            }
        } |
        Sort-Object `
            @{ Expression = { Normalize-Date $_.Event.date }; Descending = $true }, `
            @{ Expression = { $_.Priority }; Descending = $true }, `
            @{ Expression = { [string]$_.Event.id }; Descending = $false }
)

# Wikipedia rows use the project's object-name + strike-date dedupe rule.
# Keep the first stable row when an earlier import left another ID for the
# same object and date.
$wikiKeys = @{}
$dedupedEvents = New-Object System.Collections.Generic.List[object]
foreach ($entry in $sortedEventEntries) {
    $event = $entry.Event
    $sources = [string]$event.sources
    if ($sources -like '*source_wiki_uav_strikes_*') {
        $objectName = [string]$event.titleUk
        $wikiKey = ('{0}||{1}' -f (Normalize-Date $event.date), $objectName.Trim().ToLowerInvariant())
        if ($wikiKeys.ContainsKey($wikiKey)) {
            continue
        }
        $wikiKeys[$wikiKey] = $true
    }

    $dedupedEvents.Add($event) | Out-Null
}

$payload = [ordered]@{
    schemaVersion = if ($baseRoot.schemaVersion) { [int]$baseRoot.schemaVersion } else { 1 }
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    sourceFile = if ($baseRoot.sourceFile) { [string]$baseRoot.sourceFile } else { (Resolve-Path $BaseFile).Path }
    importTarget = if ($baseRoot.importTarget) { [string]$baseRoot.importTarget } else { 'Room EventEntity' }
    recordCount = $dedupedEvents.Count
    skippedCount = if ($baseRoot.skippedCount) { [int]$baseRoot.skippedCount } else { 0 }
    events = $dedupedEvents
}

New-Item -ItemType Directory -Force -Path (Split-Path $OutputFile -Parent) | Out-Null
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($OutputFile, ($payload | ConvertTo-Json -Depth 100), $utf8NoBom)

Write-Output "Merged event snapshot: $OutputFile"
Write-Output "Records exported: $($dedupedEvents.Count)"
