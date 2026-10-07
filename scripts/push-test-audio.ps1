# Push BZ Music test MP3s (including Acoustic/ and Beats/) onto the emulator
# so MediaStore can see them. Host originals live in test-audio\ (kept in the project).
#
# Usage:
#   .\scripts\push-test-audio.ps1
#   .\scripts\push-test-audio.ps1 -Serial emulator-5554

param(
    [string]$Serial = "emulator-5554"
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$Src = Join-Path $Root "test-audio"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    throw "adb not found at $adb"
}
if (-not (Test-Path $Src)) {
    throw "Missing test-audio folder: $Src"
}

$prefix = @("-s", $Serial)

Write-Host "Ensuring /sdcard/Music/Acoustic and Beats exist..."
& $adb @prefix shell "mkdir -p /sdcard/Music/Acoustic /sdcard/Music/Beats"
if ($LASTEXITCODE -ne 0) { throw "mkdir failed" }

Get-ChildItem $Src -File -Filter "*.mp3" | ForEach-Object {
    Write-Host "Push $($_.Name)"
    & $adb @prefix push $_.FullName "/sdcard/Music/$($_.Name)"
    if ($LASTEXITCODE -ne 0) { throw "push failed: $($_.Name)" }
}

foreach ($folder in @("Acoustic", "Beats")) {
    $local = Join-Path $Src $folder
    if (-not (Test-Path $local)) { continue }
    Get-ChildItem $local -File -Filter "*.mp3" | ForEach-Object {
        Write-Host "Push $folder/$($_.Name)"
        & $adb @prefix push $_.FullName "/sdcard/Music/$folder/$($_.Name)"
        if ($LASTEXITCODE -ne 0) { throw "push failed: $folder/$($_.Name)" }
    }
}

Write-Host "Triggering media scan for each MP3..."
$remoteFiles = & $adb @prefix shell "find /sdcard/Music -name '*.mp3'"
foreach ($line in ($remoteFiles -split "`n")) {
    $f = $line.Trim()
    if (-not $f) { continue }
    & $adb @prefix shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://$f'" | Out-Null
}
& $adb @prefix shell "find /sdcard/Music -name '*.mp3' -print"

Write-Host ""
Write-Host "Done. In BZ Music: Open folder and subfolders -> Music (or Device audio)."
Write-Host "Host copies remain in: $Src"
