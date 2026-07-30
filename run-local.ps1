[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Ensure-JavaHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return
    }

    $androidStudioJbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path (Join-Path $androidStudioJbr 'bin\java.exe')) {
        $env:JAVA_HOME = $androidStudioJbr
        $env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"
        return
    }

    throw 'JAVA_HOME is not set and Android Studio JBR was not found.'
}

function Ensure-EnvFile {
    $envFile = Join-Path $PSScriptRoot '.env'
    $exampleFile = Join-Path $PSScriptRoot '.env.example'

    if (-not (Test-Path $envFile)) {
        Copy-Item -Path $exampleFile -Destination $envFile
        Write-Output "Created $envFile from .env.example"
    }
}

Ensure-JavaHome
Ensure-EnvFile

$finalizeScript = Join-Path $PSScriptRoot 'tools\finalize-data.ps1'
& $finalizeScript

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
$adbPath = $adbCommand.Source
if (-not $adbPath) {
    $defaultAdbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path $defaultAdbPath) {
        $adbPath = $defaultAdbPath
    }
}

$hasDevice = $false
if ($adbPath) {
    $adbOutput = & $adbPath devices 2>$null
    $hasDevice = [bool]($adbOutput -match "`tdevice$")
}

$gradleTask = if ($hasDevice) { ':app:installDebug' } else { ':app:assembleDebug' }
Write-Output "Running Gradle task $gradleTask"
& (Join-Path $PSScriptRoot 'gradlew.bat') $gradleTask
