# Sets up and deploys the Utility Client key server.
#
#   .\setup-keys.ps1
#
# The first run pauses for a Cloudflare sign-in in your browser. That is the only step
# that needs you. Everything after it, including generating the admin token, creating
# storage, uploading the secret and deploying, happens automatically.

$ErrorActionPreference = 'Stop'

# Node and wrangler were installed into the standard locations, which are not always on
# PATH in a fresh PowerShell session.
$extraPaths = @(
    'C:\Program Files\nodejs',
    "$env:APPDATA\npm"
)
foreach ($dir in $extraPaths) {
    if ((Test-Path $dir) -and ($env:Path -notlike "*$dir*")) {
        $env:Path = "$dir;$env:Path"
    }
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host ''
    Write-Host '  Node.js is not installed. Install it, then run this again:' -ForegroundColor Red
    Write-Host '    winget install OpenJS.NodeJS.LTS'
    Write-Host ''
    Read-Host 'Press Enter to close'
    exit 1
}

Write-Host ''
Write-Host '  Utility Client - key server setup' -ForegroundColor Cyan
Write-Host '  -------------------------------' -ForegroundColor Cyan
Write-Host ''

Push-Location (Join-Path $PSScriptRoot 'worker')
try {
    & node setup.mjs
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($code -eq 0) {
    Write-Host ''
    Write-Host '  Done. The address and admin token are printed above.' -ForegroundColor Green
    Write-Host '  Copy the token into a password manager before you close this window.' -ForegroundColor Yellow
} else {
    Write-Host ''
    Write-Host '  Setup did not finish. Read the message above.' -ForegroundColor Yellow
}

Write-Host ''
Read-Host 'Press Enter to close'
exit $code
