[CmdletBinding()]
param(
    [string]$InputDirectory,
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'

if (-not $InputDirectory) {
    $InputDirectory = Join-Path $PSScriptRoot '..\data'
}

if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $PSScriptRoot '..\data\merged'
}

function Get-FirstNonEmptyValue {
    param(
        [object]$Record,
        [string[]]$PathCandidates
    )

    foreach ($path in $PathCandidates) {
        $current = $Record
        $segments = $path -split '\.'
        $found = $true

        foreach ($segment in $segments) {
            if ($null -eq $current) {
                $found = $false
                break
            }

            $properties = $current.PSObject.Properties.Name
            if (-not ($properties -contains $segment)) {
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

function Get-ObjectName {
    param([object]$Record)

    $title = Get-FirstNonEmptyValue -Record $Record -PathCandidates @('title.en', 'title.uk', 'title')
    if ($title) { return $title }

    $assetName = Get-FirstNonEmptyValue -Record $Record -PathCandidates @('asset.assetName')
    if ($assetName) { return $assetName }

    $placeRaw = Get-FirstNonEmptyValue -Record $Record -PathCandidates @('placeRaw')
    if ($placeRaw) { return $placeRaw }

    $targetSummaryRaw = Get-FirstNonEmptyValue -Record $Record -PathCandidates @('targetSummaryRaw')
    if ($targetSummaryRaw) { return $targetSummaryRaw }

    return Get-FirstNonEmptyValue -Record $Record -PathCandidates @('id')
}

function Get-RecordDate {
    param([object]$Record)
    return Get-FirstNonEmptyValue -Record $Record -PathCandidates @('date')
}

function Normalize-KeyPart {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ''
    }

    return $Value.Trim().ToLowerInvariant()
}

function Get-CyrillicEncoding {
    if (-not $script:CyrillicEncoding) {
        try {
            $script:CyrillicEncoding = [System.Text.Encoding]::GetEncoding(1251)
        }
        catch {
            [System.Text.Encoding]::RegisterProvider([System.Text.CodePagesEncodingProvider]::Instance)
            $script:CyrillicEncoding = [System.Text.Encoding]::GetEncoding(1251)
        }
    }

    return $script:CyrillicEncoding
}

function Get-TextLetterCount {
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return 0
    }

    return ([regex]::Matches($Text, '\p{L}')).Count
}

function Get-MojibakeMarkerCount {
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return 0
    }

    return ([regex]::Matches($Text, '[\u0420\u0421\u0412\u0406\u0404\u0407\u0490\u0402\u0403\u0408\u0409\u040A\u040B\u040C\u040D\u040E\u040F]')).Count
}

function Get-MojibakeRatio {
    param([string]$Text)

    $letterCount = Get-TextLetterCount -Text $Text
    if ($letterCount -le 0) {
        return 0
    }

    return (Get-MojibakeMarkerCount -Text $Text) / [double]$letterCount
}

function Should-RepairMojibakeText {
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $false
    }

    return (Get-MojibakeRatio -Text $Text) -ge 0.35
}

function Repair-MojibakeText {
    param([string]$Text)

    if (-not (Should-RepairMojibakeText -Text $Text)) {
        return $Text
    }

    $originalLength = $Text.Length
    $originalMojibakeRatio = Get-MojibakeRatio -Text $Text
    $bytes = (Get-CyrillicEncoding).GetBytes($Text)
    $candidate = [System.Text.Encoding]::UTF8.GetString($bytes)
    $candidateLength = $candidate.Length
    $candidateMojibakeRatio = Get-MojibakeRatio -Text $candidate

    if (
        $candidateLength -lt $originalLength -and
        $candidateMojibakeRatio -lt $originalMojibakeRatio
    ) {
        return $candidate
    }

    return $Text
}

function Repair-JsonValue {
    param([object]$Value)

    if ($null -eq $Value) {
        return $null
    }

    if ($Value -is [string]) {
        return Repair-MojibakeText -Text $Value
    }

    if ($Value -is [System.Collections.IDictionary]) {
        $copy = [ordered]@{}
        foreach ($key in $Value.Keys) {
            $copy[$key] = Repair-JsonValue -Value $Value[$key]
        }

        return [pscustomobject]$copy
    }

    if ($Value -is [System.Collections.IList]) {
        $items = New-Object System.Collections.Generic.List[object]
        foreach ($item in $Value) {
            $items.Add((Repair-JsonValue -Value $item)) | Out-Null
        }

        return ,$items.ToArray()
    }

    if ($Value -is [pscustomobject]) {
        $copy = [ordered]@{}
        foreach ($property in $Value.PSObject.Properties) {
            $copy[$property.Name] = Repair-JsonValue -Value $property.Value
        }

        return [pscustomobject]$copy
    }

    return $Value
}
function Copy-Record {
    param([object]$Record)

    $copy = [ordered]@{}
    foreach ($property in $Record.PSObject.Properties) {
        $copy[$property.Name] = Repair-JsonValue -Value $property.Value
    }

    return [pscustomobject]$copy
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$files = Get-ChildItem -Path $InputDirectory -Filter '*.json' -File | Sort-Object Name
if (-not $files) {
    throw "No JSON files found in $InputDirectory"
}

$seen = @{}
$uniqueRecords = New-Object System.Collections.Generic.List[object]
$duplicateGroups = @{}
$sourceSummaries = New-Object System.Collections.Generic.List[object]
$duplicateRecordTotal = 0

foreach ($file in $files) {
    $root = Get-Content -Path $file.FullName -Raw | ConvertFrom-Json
    $root = Repair-JsonValue -Value $root

    $arrayName = $null
    if ($root.PSObject.Properties.Name -contains 'events') {
        $arrayName = 'events'
    }
    elseif ($root.PSObject.Properties.Name -contains 'records') {
        $arrayName = 'records'
    }
    else {
        continue
    }

    $datasetId = $null
    if ($root.PSObject.Properties.Name -contains 'datasetId') {
        $datasetId = [string]$root.datasetId
    }

    $records = @($root.$arrayName)
    $sourceSummaries.Add([pscustomobject]@{
        file = $file.Name
        datasetId = $datasetId
        arrayName = $arrayName
        recordCount = $records.Count
    }) | Out-Null

    for ($index = 0; $index -lt $records.Count; $index++) {
        $record = $records[$index]
        $objectName = Get-ObjectName -Record $record
        $recordDate = Get-RecordDate -Record $record
        $key = "{0}||{1}" -f (Normalize-KeyPart $objectName), (Normalize-KeyPart $recordDate)

        if (-not $seen.ContainsKey($key)) {
            $copy = Copy-Record -Record $record
            $copy | Add-Member -NotePropertyName '_sourceFile' -NotePropertyValue $file.Name
            $copy | Add-Member -NotePropertyName '_sourceDatasetId' -NotePropertyValue $datasetId
            $copy | Add-Member -NotePropertyName '_sourceArray' -NotePropertyValue $arrayName
            $copy | Add-Member -NotePropertyName '_sourceIndex' -NotePropertyValue $index
            $copy | Add-Member -NotePropertyName '_dedupeKey' -NotePropertyValue $key
            $copy | Add-Member -NotePropertyName '_objectName' -NotePropertyValue $objectName
            $uniqueRecords.Add($copy) | Out-Null
            $seen[$key] = $true
        }
        else {
            if (-not $duplicateGroups.ContainsKey($key)) {
                $duplicateGroups[$key] = New-Object System.Collections.Generic.List[object]
            }

            $duplicateGroups[$key].Add([pscustomobject]@{
                id = Get-FirstNonEmptyValue -Record $record -PathCandidates @('id')
                objectName = $objectName
                date = $recordDate
                sourceFile = $file.Name
                sourceDatasetId = $datasetId
                sourceArray = $arrayName
            }) | Out-Null
            $duplicateRecordTotal++
        }
    }
}

$duplicateGroupsList = New-Object System.Collections.Generic.List[object]
foreach ($entry in $duplicateGroups.GetEnumerator() | Sort-Object Name) {
    $group = $entry.Value
    if ($group.Count -lt 1) { continue }

    $first = $group[0]
    $duplicateGroupsList.Add([pscustomobject]@{
        objectName = $first.objectName
        date = $first.date
        key = $entry.Key
        count = $group.Count
        records = $group
    }) | Out-Null
}

$generatedAt = (Get-Date).ToUniversalTime().ToString('o')
$mergedFile = Join-Path $OutputDirectory 'merged_deduped.json'
$duplicatesJson = Join-Path $OutputDirectory 'duplicate_groups.json'
$duplicatesTxt = Join-Path $OutputDirectory 'duplicate_groups.txt'

$mergedPayload = [ordered]@{
    generatedAt = $generatedAt
    inputDirectory = (Resolve-Path $InputDirectory).Path
    sourceFiles = $sourceSummaries
    dedupeStrategy = 'object name + date'
    uniqueRecordCount = $uniqueRecords.Count
    duplicateRecordCount = $duplicateRecordTotal
    records = $uniqueRecords
}

$duplicatePayload = [ordered]@{
    generatedAt = $generatedAt
    dedupeStrategy = 'object name + date'
    duplicateGroupCount = $duplicateGroupsList.Count
    duplicateRecordCount = $duplicateRecordTotal
    groups = $duplicateGroupsList
}

$mergedPayload | ConvertTo-Json -Depth 100 | Set-Content -Path $mergedFile -Encoding utf8
$duplicatePayload | ConvertTo-Json -Depth 100 | Set-Content -Path $duplicatesJson -Encoding utf8

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add("Generated at: $generatedAt") | Out-Null
$reportLines.Add("Input directory: $((Resolve-Path $InputDirectory).Path)") | Out-Null
$reportLines.Add("Unique records: $($uniqueRecords.Count)") | Out-Null
$reportLines.Add("Duplicate records removed: $duplicateRecordTotal") | Out-Null
$reportLines.Add("Duplicate groups: $($duplicateGroupsList.Count)") | Out-Null
$reportLines.Add('') | Out-Null

foreach ($group in $duplicateGroupsList) {
    $reportLines.Add("Object: $($group.objectName)") | Out-Null
    $reportLines.Add("Date: $($group.date)") | Out-Null
    $reportLines.Add("Key: $($group.key)") | Out-Null
    $reportLines.Add("Count: $($group.count)") | Out-Null
    foreach ($record in $group.records) {
        $reportLines.Add("  - $($record.sourceFile) :: $($record.id)") | Out-Null
    }
    $reportLines.Add('') | Out-Null
}

$reportLines | Set-Content -Path $duplicatesTxt -Encoding utf8

Write-Output "Merged file: $mergedFile"
Write-Output "Duplicate JSON: $duplicatesJson"
Write-Output "Duplicate report: $duplicatesTxt"
Write-Output "Unique records: $($uniqueRecords.Count)"
Write-Output "Duplicate records removed: $duplicateRecordTotal"
Write-Output "Duplicate groups: $($duplicateGroupsList.Count)"




