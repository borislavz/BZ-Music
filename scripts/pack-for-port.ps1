# Creates a portable zip for moving BZ Music to another PC.
# Includes signing secrets — treat the zip like a password vault.
#
# Usage:
#   .\scripts\pack-for-port.ps1
#   .\scripts\pack-for-port.ps1 -OutZip D:\backup\BZ-Music-port.zip
#   .\scripts\pack-for-port.ps1 -IncludeMavenCache   # adds ~550MB offline deps

param(
    [string]$OutZip = "C:\develop\BZ-Music-port.zip",
    [switch]$IncludeMavenCache
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$WinTar = "$env:SystemRoot\System32\tar.exe"

function Copy-Tree($src, $dest) {
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    & robocopy $src $dest /E /NFL /NDL /NJH /NJS /nc /ns /np /R:1 /W:1 | Out-Null
    if ($LASTEXITCODE -ge 8) { throw "robocopy failed ($LASTEXITCODE): $src -> $dest" }
}

function Remove-Tree($path) {
    if (-not (Test-Path $path)) { return }
    # Long-path friendly delete
    & cmd.exe /c "rmdir /s /q `"\\?\$path`"" | Out-Null
    if (Test-Path $path) {
        Remove-Item -LiteralPath $path -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$required = @(
    "app", "gradle", "gradlew.bat", "settings.gradle.kts", "build.gradle.kts",
    "muzika-release.jks", "keystore.properties", "play-store", "PORTING.md"
)
foreach ($r in $required) {
    if (-not (Test-Path (Join-Path $Root $r))) {
        throw "Missing required path for port pack: $r"
    }
}

$stage = Join-Path $env:TEMP ("BZMusicPort" + [guid]::NewGuid().ToString("N").Substring(0, 8))
New-Item -ItemType Directory -Path $stage | Out-Null

$includeDirs = @("app", "gradle", "play-store", "scripts", "test-audio")
if ($IncludeMavenCache) { $includeDirs += "local-maven-repo" }
# Cursor project rules (go / Version5 restore) — small, useful if using Cursor on the new PC
if (Test-Path (Join-Path $Root ".cursor\rules")) { $includeDirs += ".cursor" }

$includeFiles = @(
    "gradlew", "gradlew.bat", "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
    "resolve-vlc-temp.gradle.kts", ".gitignore", "README.md", "PORTING.md", "PRIVACY.md",
    "PLAY_STORE.md", "muzika-release.jks", "keystore.properties", "keystore.properties.example",
    "local.properties.example", "gradle-truststore.jks"
)

foreach ($name in $includeDirs) {
    $src = Join-Path $Root $name
    if (Test-Path $src) {
        Write-Host "Copying $name ..."
        Copy-Tree $src (Join-Path $stage $name)
    }
}
foreach ($name in $includeFiles) {
    $src = Join-Path $Root $name
    if (Test-Path $src) {
        Copy-Item $src (Join-Path $stage $name) -Force
    }
}

foreach ($k in @("app\build", "build", ".gradle", ".idea")) {
    Remove-Tree (Join-Path $stage $k)
}

$aabDir = Join-Path $stage "release-artifacts"
New-Item -ItemType Directory -Path $aabDir | Out-Null
foreach ($a in @(
    (Join-Path $Root "app\build\outputs\bundle\release\app-release.aab"),
    (Join-Path $Root "BZ Music.aab")
)) {
    if (Test-Path $a) {
        Copy-Item $a (Join-Path $aabDir "app-release.aab") -Force
        break
    }
}

@"
BZ Music portable pack
Created: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
Source: $Root
Contains signing keystore: YES (muzika-release.jks + keystore.properties)
Maven cache included: $IncludeMavenCache
Next step on new PC: extract, then run scripts\setup-new-machine.ps1
See PORTING.md
"@ | Set-Content (Join-Path $stage "TRANSFER_MANIFEST.txt") -Encoding UTF8

@"
THIS ZIP CONTAINS PLAY SIGNING SECRETS.
- muzika-release.jks
- keystore.properties
Store offline securely. Do not upload to a public drive or public git remote.
"@ | Set-Content (Join-Path $stage "SECRETS_INCLUDED.txt") -Encoding UTF8

if (Test-Path $OutZip) { Remove-Item $OutZip -Force }
$outDir = Split-Path $OutZip -Parent
if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

Write-Host "Compressing to $OutZip ..."
if (Test-Path $WinTar) {
    Push-Location $stage
    & $WinTar -a -cf $OutZip *
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw "tar failed with exit $LASTEXITCODE" }
    Pop-Location
} else {
    Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $OutZip -CompressionLevel Optimal
}

Remove-Tree $stage
$item = Get-Item $OutZip
Write-Host ""
Write-Host "Created: $($item.FullName)"
Write-Host ("Size: {0:N1} MB" -f ($item.Length / 1MB))
if (-not $IncludeMavenCache) {
    Write-Host "Note: local-maven-repo was skipped (smaller zip). Copy that folder separately if offline builds are needed,"
    Write-Host "      or re-run with -IncludeMavenCache."
}
Write-Host "Copy this zip to the other PC, extract, run scripts\setup-new-machine.ps1"
