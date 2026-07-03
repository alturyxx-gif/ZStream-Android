# dev-run.ps1 — builds the plugin and pushes it to all connected devices/emulators
# Run this before launching the app from Android Studio.
$ErrorActionPreference = "Stop"

$PluginDir = Join-Path $PSScriptRoot "zstream-plugin"
$PluginApk = Join-Path $PluginDir "plugin\build\outputs\apk\debug\plugin-debug.apk"
$Dest = "/sdcard/Android/data/com.zstream.android/files/plugin-debug.apk"

Write-Host "▶ Building plugin..."
Push-Location $PluginDir
& .\gradlew.bat assembleDebug -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Pop-Location

$Devices = adb devices | Select-String '^\S+\s+device$' | ForEach-Object { ($_ -split '\s+')[0] }

if (-not $Devices) {
    Write-Host "✗ No devices connected."
    exit 1
}

$Count = @($Devices).Count
Write-Host "▶ Pushing plugin APK to $Count device(s)..."
foreach ($Serial in $Devices) {
    Write-Host "  → $Serial"
    adb -s $Serial push $PluginApk $Dest
    if ($LASTEXITCODE -ne 0) { Write-Warning "Failed to push to $Serial" }
}

Write-Host "✓ Done. Launch the app and tap 'Dev: Sideload plugin'."
