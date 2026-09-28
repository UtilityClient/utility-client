# Kills any running Minecraft game process, then installs the freshly built mod.
# Safe to run repeatedly. The Minecraft Launcher itself is left alone; only the game JVM
# is terminated, and it is identified by the fabric/minecraft client arguments rather than
# by killing every java process on the machine.

$ErrorActionPreference = 'Stop'
# Derived from the script's own location. Hardcoding the project path here is a trap: this
# directory name contains a non-ASCII character, and Windows PowerShell reads a script file
# as ANSI unless it has a BOM, which silently corrupts the path.
$project = $PSScriptRoot
$src     = Join-Path $project 'build\libs\utility-client-0.1.0.jar'
$dst     = 'C:\Users\Samy Oubaha\AppData\Roaming\.minecraft\installations\fabric-loader-26.2\mods\utility-client-0.1.0.jar'
$staging = "$dst.staging"

if (-not (Test-Path -LiteralPath $src)) {
    Write-Host "no build found at $src - run gradlew build first" -ForegroundColor Red
    exit 1
}

# --- stop the game -------------------------------------------------------------------
$killed = @()
foreach ($p in @(Get-Process -Name java,javaw -ErrorAction SilentlyContinue)) {
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)" -ErrorAction SilentlyContinue).CommandLine
    if ($cmd -and ($cmd -match 'KnotClient' -or $cmd -match 'fabric' -or $cmd -match 'net\.minecraft')) {
        try {
            Stop-Process -Id $p.Id -Force
            $killed += $p.Id
        } catch {
            Write-Host "  could not stop pid $($p.Id): $($_.Exception.Message)" -ForegroundColor Yellow
        }
    }
}
if ($killed.Count -gt 0) {
    Start-Sleep -Milliseconds 1200
    Write-Host "stopped Minecraft (pid $($killed -join ', '))"
} else {
    Write-Host "Minecraft was not running"
}

# --- install -------------------------------------------------------------------------
Copy-Item -LiteralPath $src -Destination $staging -Force

# Verify the archive is a readable zip containing the classes we care about before it is
# allowed to replace a working install. A truncated copy is far more annoying to debug
# later than a failed install now.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($staging)
try {
    $count = $zip.Entries.Count
    $hasMain  = [bool]($zip.Entries | Where-Object { $_.FullName -eq 'dev/utilityclient/UtilityClient.class' })
    $hasMixin = [bool]($zip.Entries | Where-Object { $_.FullName -eq 'dev/utilityclient/mixin/AttackMixin.class' })
    $hasJson  = [bool]($zip.Entries | Where-Object { $_.FullName -eq 'utilityclient.mixins.json' })
} finally {
    $zip.Dispose()
}
if ($count -lt 50 -or -not $hasMain -or -not $hasMixin -or -not $hasJson) {
    Remove-Item -LiteralPath $staging -Force -ErrorAction SilentlyContinue
    Write-Host "staged jar failed validation (entries=$count main=$hasMain mixin=$hasMixin json=$hasJson)" -ForegroundColor Red
    exit 1
}

New-Item -ItemType Directory -Force -Path (Split-Path $dst) | Out-Null
Move-Item -LiteralPath $staging -Destination $dst -Force

$size = (Get-Item -LiteralPath $dst).Length
Write-Host "installed $size bytes, $count entries -> $dst" -ForegroundColor Green
Write-Host "start Minecraft and it should load clean."
