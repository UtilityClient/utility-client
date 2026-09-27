# Builds the mod and installs it into your Minecraft profile.
#
#   .\install.ps1
#
# The previous version of this copied the jar straight over the top of the installed one.
# If Minecraft was running, or the copy was interrupted, it left a corrupt jar behind and
# the game failed with "Failed to load class file" and "ZipFile invalid LOC header".
# This version refuses to run while the game is open, copies to a temporary file, checks
# the archive actually opens, and only then moves it into place.

$ErrorActionPreference = 'Stop'

$profile = Join-Path $env:APPDATA '.minecraft\installations\fabric-loader-26.2'
$mods = Join-Path $profile 'mods'
$target = Join-Path $mods 'utility-client-0.1.0.jar'
$source = Join-Path $PSScriptRoot 'build\libs\utility-client-0.1.0.jar'

# --- refuse to run while the game is open ---
$running = Get-Process -Name 'java', 'javaw' -ErrorAction SilentlyContinue |
    Where-Object { $_.Path -and $_.Path -notlike '*OpenJDK*jdk*' }
if ($running) {
    Write-Host ''
    Write-Host '  Close Minecraft first.' -ForegroundColor Red
    Write-Host '  Copying a jar over a running game is what corrupted the last install.' -ForegroundColor Red
    Write-Host ''
    Read-Host 'Press Enter to close'
    exit 1
}

if (-not (Test-Path $source)) {
    Write-Host "  Built jar not found at $source" -ForegroundColor Red
    Read-Host 'Press Enter to close'
    exit 1
}

# --- build ---
$env:JAVA_HOME = 'C:\Users\Samy Oubaha\AppData\Local\Packages\Microsoft.4297127D64EC6_8wekyb3d8bbwe\LocalCache\Local\runtime\java-runtime-epsilon\windows-x64\java-runtime-epsilon'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host ''
Write-Host '  Building...' -ForegroundColor Cyan
Push-Location $PSScriptRoot
try {
    & '.\gradlew.bat' build
    if ($LASTEXITCODE -ne 0) {
        Write-Host '  Build failed, see the output above.' -ForegroundColor Red
        Read-Host 'Press Enter to close'
        exit 1
    }
} finally {
    Pop-Location
}

# --- copy to a staging file, verify, then move ---
New-Item -ItemType Directory -Path $mods -Force | Out-Null
$staging = Join-Path $mods '.utility-client-staging.jar'

try {
    Copy-Item -LiteralPath $source -Destination $staging -Force

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($staging)
    $count = $zip.Entries.Count
    $hasMain = [bool]($zip.Entries | Where-Object { $_.FullName -eq 'fabric.mod.json' })
    $zip.Dispose()

    if (-not $hasMain -or $count -lt 10) {
        throw "Staged jar looks wrong: $count entries, fabric.mod.json present: $hasMain"
    }

    # Move is atomic within the same volume, so the target is never half written.
    Move-Item -LiteralPath $staging -Destination $target -Force

    $size = (Get-Item -LiteralPath $target).Length
    Write-Host ''
    Write-Host "  Installed OK: $count files, $size bytes" -ForegroundColor Green
    Write-Host "  $target"
} catch {
    if (Test-Path -LiteralPath $staging) {
        Remove-Item -LiteralPath $staging -Force -ErrorAction SilentlyContinue
    }
    Write-Host ''
    Write-Host "  Install failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host '  The previous jar was left untouched.' -ForegroundColor Yellow
    Read-Host 'Press Enter to close'
    exit 1
}

Write-Host ''
Read-Host 'Press Enter to close'
