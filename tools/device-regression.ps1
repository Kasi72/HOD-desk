param(
    [string]$ApkPath = "",
    [string]$PackageName = "com.hoddesk.app",
    [int]$WaitSeconds = 3
)

$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { throw "adb was not found at $adb" }
$devices = (& $adb devices) | Select-String "\sdevice$"
if (-not $devices) { throw "No authorized Android device is connected." }

if ($ApkPath) {
    if (-not (Test-Path $ApkPath)) { throw "APK not found: $ApkPath" }
    & $adb install -r $ApkPath | Write-Host
    if ($LASTEXITCODE -ne 0) { throw "APK installation failed." }
}

& $adb logcat -c
& $adb shell input keyevent KEYCODE_BACK | Out-Null
& $adb shell monkey -p $PackageName 1 | Out-Host
Start-Sleep -Seconds $WaitSeconds

$focused = (& $adb shell dumpsys activity activities) -join "`n"
if ($focused -notmatch [regex]::Escape($PackageName)) { throw "The app did not become the focused activity." }

& $adb shell input keyevent KEYCODE_BACK | Out-Null
$ui = ""
for ($attempt = 0; $attempt -lt 5; $attempt++) {
    & $adb shell uiautomator dump /sdcard/hoddesk-ui.xml | Out-Null
    $ui = (& $adb shell cat /sdcard/hoddesk-ui.xml) -join "`n"
    if ($ui -match "com.hoddesk.app" -and ($ui -match "Today")) { break }
    Start-Sleep -Seconds 1
    & $adb shell monkey -p $PackageName 1 | Out-Null
}
foreach ($label in @("Today", "Review", "Tasks", "Calendar", "Library")) {
    if ($ui -notmatch [regex]::Escape($label)) { throw "Missing primary tab in UI hierarchy after retries: $label" }
}

$crashes = (& $adb logcat -d -b crash) -join "`n"
if ($crashes -match "FATAL EXCEPTION|Process: $PackageName") {
    Write-Error $crashes
    throw "A crash was recorded during cold-start validation."
}

Write-Host "PASS: cold start, primary tabs, and crash buffer validated on the connected device."
