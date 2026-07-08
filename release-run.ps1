# release-run.ps1 - builds the release plugin and pushes it to all connected devices/emulators
# Run this before launching the app from Android Studio.
# Note: UI symbols (✓, ✗, ▶, →) removed to avoid encoding issues on Windows.

$PluginDir = Join-Path $PSScriptRoot "zstream-plugin"
$PluginApk = Join-Path $PluginDir "plugin\build\outputs\apk\release\plugin-release.apk"
$Dest = "/sdcard/Android/data/com.zstream.android/files/plugin-debug.apk"

# ---------------------------------------------------------------------------
# Ensure zstream-plugin submodule is initialized
# ---------------------------------------------------------------------------
$CmakeLists = Join-Path $PluginDir "plugin\src\main\cpp\CMakeLists.txt"
if (-not (Test-Path $CmakeLists)) {
    Write-Host "  zstream-plugin submodule not found, initializing..."
    & git -C $PSScriptRoot submodule update --init --recursive
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to initialize submodules. Make sure you have access to the plugin repo."
        exit 1
    }
}

# ---------------------------------------------------------------------------
# Ensure zstream-plugin/local.properties has sdk.dir
# ---------------------------------------------------------------------------
$LocalProps = Join-Path $PluginDir "local.properties"
if (-not (Test-Path $LocalProps) -or -not (Select-String -Path $LocalProps -Pattern "sdk\.dir" -Quiet)) {
    $SdkDir = $env:ANDROID_HOME
    if (-not $SdkDir) {
        $SdkDir = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }
    if (-not (Test-Path $SdkDir)) {
        Write-Error "Android SDK not found. Set the ANDROID_HOME environment variable or install Android Studio."
        exit 1
    }
    $SdkDirFwd = $SdkDir.Replace("\", "\\")
    Add-Content -Path $LocalProps -Value "sdk.dir=$SdkDirFwd"
    Write-Host "  Created $LocalProps (sdk.dir=$SdkDirFwd)"
}

Write-Host "Building release plugin..."
Push-Location $PluginDir
& .\gradlew.bat assembleRelease -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Pop-Location

$Devices = adb devices | Select-String '^\S+\s+device$' | ForEach-Object { ($_ -split '\s+')[0] }

if (-not $Devices) {
    Write-Host "No devices connected."
    exit 1
}

$Count = @($Devices).Count
Write-Host "Pushing release plugin APK to $Count device(s)..."
$SuccessCount = 0
foreach ($Serial in $Devices) {
    Write-Host "  -> $Serial"
    $Output = adb -s $Serial push $PluginApk $Dest 2>&1
    if ($LASTEXITCODE -eq 0) {
        $SuccessCount++
    }
}

if ($SuccessCount -eq 0) {
    Write-Host "No devices received the APK."
    exit 1
}

Write-Host "Done. Pushed to $SuccessCount device(s). Launch the app and tap 'Dev: Sideload plugin'."
