# Opens the window on a computer that only runs the client. The server runs on another computer.
# Run it with run-client.bat (double-click) or:  powershell -ExecutionPolicy Bypass -File scripts\run-client.ps1
#
#   -CheckOnly   only test the secure connection to the server (SmokeTestMain); do not open the window
param(
    [switch]$CheckOnly
)

. "$PSScriptRoot\common.ps1"

Assert-Tools
Import-SavedSettings

if (-not $env:HRM_TRUSTSTORE_PASSWORD -or -not (Test-Path 'config\client.truststore') `
        -or -not (Test-Path 'config\client.properties')) {
    Fail "The client setup has not been done on this computer. Run setup-client.bat first."
}

Build-Project

$server = Get-Setting 'config\client.properties' 'server.host' 'localhost'
if ($CheckOnly) {
    Write-Step "Testing the secure connection to the HR server at $server"
    Invoke-Java 'com.bhel.hrm.client.SmokeTestMain'
    exit $LASTEXITCODE
}
Write-Step "Opening the window (HR server at $server)"
Invoke-Java 'com.bhel.hrm.client.HrmClientMain'
