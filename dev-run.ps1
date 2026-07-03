# dev-run.ps1 - builds the plugin and pushes it to all connected devices/emulators
# Run this before launching the app from Android Studio.
$ErrorActionPreference = "Stop"

$PluginDir = Join-Path $PSScriptRoot "zstream-plugin"
$PluginApk = Join-Path $PluginDir "plugin\build\outputs\apk\debug\plugin-debug.apk"
$Dest = "/sdcard/Android/data/com.zstream.android/files/plugin-debug.apk"

# ---------------------------------------------------------------------------
# Ensure zstream-plugin/local.properties has sdk.dir
# ---------------------------------------------------------------------------
$LocalProps = Join-Path $PluginDir "local.properties"
if (-not (Test-Path $LocalProps) -or -not (Select-String -Path $LocalProps -Pattern "sdk\.dir" -Quiet)) {
    $SdkDir = $env:ANDROID_HOME
    if (-not $SdkDir) {
        # Common default install location on Windows
        $SdkDir = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }
    if (-not (Test-Path $SdkDir)) {
        Write-Error "Android SDK not found. Set the ANDROID_HOME environment variable or install Android Studio."
        exit 1
    }
    # CMake/Gradle expects forward slashes
    $SdkDirFwd = $SdkDir.Replace("\", "\\")
    Add-Content -Path $LocalProps -Value "sdk.dir=$SdkDirFwd"
    Write-Host "  Created $LocalProps (sdk.dir=$SdkDirFwd)"
}

Write-Host ">> Building plugin..."
Push-Location $PluginDir
& .\gradlew.bat assembleDebug -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Pop-Location

$Devices = adb devices | Select-String '^\S+\s+device$' | ForEach-Object { ($_ -split '\s+')[0] }

if (-not $Devices) {
    Write-Host "No devices connected."
    exit 1
}

$Count = @($Devices).Count
Write-Host ">> Pushing plugin APK to $Count device(s)..."
foreach ($Serial in $Devices) {
    Write-Host "  -> $Serial"
    adb -s $Serial push $PluginApk $Dest
    if ($LASTEXITCODE -ne 0) { Write-Warning "Failed to push to $Serial" }
}

Write-Host "Done. Launch the app and tap 'Dev: Sideload plugin'."
