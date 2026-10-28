# One-time setup for a computer that only runs the client and connects to the HR server on another computer.
# Nothing for the server is needed here: no MySQL, no database password, no server keys.
# Run it with setup-client.bat (double-click) or:  powershell -ExecutionPolicy Bypass -File scripts\setup-client.ps1
#
#   -ServerAddress 192.168.1.20   the server computer's IP address or name (asked for if not given)
#   -Certificate path             the server certificate copied from the server computer (default config\server.cer)
#   -NoSaveSettings               do not save the password in the Windows user settings (for testing)
param(
    [string]$ServerAddress,
    [string]$Certificate = 'config\server.cer',
    [switch]$NoSaveSettings
)

. "$PSScriptRoot\common.ps1"

Write-Host ""
Write-Host "BHEL HR Management System: client-only setup" -ForegroundColor Green
Write-Host ""

Assert-Tools
Import-SavedSettings

# Everything is checked before anything changes, so a mistake or a "no" leaves a working setup exactly as it was.
# 1. The server's public certificate. It is not a secret, so no password comes with it.
if (-not (Test-Path $Certificate)) {
    Fail ("The server certificate $Certificate was not found. Copy config\server.cer from the server computer " +
          "into this computer's config folder first (see docs\two-computer-setup.md).")
}
# A password made up here and not saved could never be typed again, so the truststore would be unusable.
if ($NoSaveSettings -and -not $env:HRM_TRUSTSTORE_PASSWORD) {
    Fail "With -NoSaveSettings, first set HRM_TRUSTSTORE_PASSWORD yourself."
}

# 2. Which computer the server is on.
$settings = 'config\client.properties'
if (-not $ServerAddress) {
    $current = Get-Setting $settings 'server.host' 'localhost'
    $ServerAddress = Read-Host "The server computer's IP address or name [$current]"
    if (-not $ServerAddress) { $ServerAddress = $current }
}
Assert-Address $ServerAddress

# 3. Trust the certificate, protected by this computer's own password (it never has to match the server's).
if (-not $env:HRM_TRUSTSTORE_PASSWORD) {
    $env:HRM_TRUSTSTORE_PASSWORD = New-RandomPassword
}
Write-Step "Trusting the server certificate"
Write-Host "Compare the SHA256 fingerprint below with the one the server computer showed. Type yes only if they match."
& cmd /c 'scripts\import-server-cert.bat' $Certificate
if ($LASTEXITCODE -ne 0) { Fail "The server certificate was not trusted. Nothing on this computer was changed." }
# Saved only now: a refused certificate must not replace the password an existing truststore still needs.
if (-not $NoSaveSettings) {
    [Environment]::SetEnvironmentVariable('HRM_TRUSTSTORE_PASSWORD', $env:HRM_TRUSTSTORE_PASSWORD, 'User')
}

# 4. Point the client at the server. Every other line of client.properties is kept.
if (-not (Test-Path $settings)) {
    Copy-Item 'config\client.example.properties' $settings -ErrorAction Stop
    Write-Host "Created $settings"
}
Set-Setting $settings 'server.host' $ServerAddress
Write-Host "Set server.host=$ServerAddress in $settings"

# 4. Compile the program. Nothing is started.
Build-Project

Write-Host ""
Write-Host "Setup is finished. Start the program with run-client.bat (run-client.bat -CheckOnly tests the connection)." -ForegroundColor Green
