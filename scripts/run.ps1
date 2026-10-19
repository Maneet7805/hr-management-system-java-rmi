# Starts everything that is needed and opens the window: MySQL, the server, then the client.
# When the window is closed the server is stopped again (if this script started it).
# Run it with run.bat (double-click) or:  powershell -ExecutionPolicy Bypass -File scripts\run.ps1
#
#   -CheckOnly   start MySQL and the server, check they work, stop again, and do not open the window
param(
    [switch]$CheckOnly
)

. "$PSScriptRoot\common.ps1"

Assert-Tools
Import-SavedSettings

$missing = @('HRM_DB_PASSWORD', 'HRM_KEYSTORE_PASSWORD', 'HRM_TRUSTSTORE_PASSWORD') |
    Where-Object { -not [Environment]::GetEnvironmentVariable($_, 'Process') }
if ($missing -or -not (Test-Path 'config\server.keystore') -or -not (Test-Path 'config\server.properties') `
        -or -not (Test-Path 'config\client.properties') -or -not (Test-Path 'config\client.truststore')) {
    Fail "The one-time setup has not been done on this computer. Run setup.bat first."
}

Start-MySql
Build-Project

$serverPort = [int](Get-Setting 'config\server.properties' 'rmi.port' '1099')
$server = $null
if (Test-Port $serverPort) {
    Write-Host "The server is already running."
} else {
    Write-Step "Starting the server"
    New-Item -ItemType Directory -Force 'target' | Out-Null
    $output = Join-Path $script:Root 'target\server.log'
    $errors = Join-Path $script:Root 'target\server-errors.log'
    $server = Start-Process -FilePath 'java' -WindowStyle Hidden -PassThru -ErrorAction Stop `
        -ArgumentList @('-cp', $script:ClassPath, 'com.bhel.hrm.server.HrmServerMain') `
        -RedirectStandardOutput $output -RedirectStandardError $errors
    if (-not (Wait-ForPort $serverPort 40 $server)) {
        Write-Host (Get-Content $output, $errors -ErrorAction SilentlyContinue | Out-String)
        if (-not $server.HasExited) { Stop-Process -Id $server.Id -Force }
        Fail "The server did not start. The messages above say why (also in target\server.log)."
    }
}

try {
    if ($CheckOnly) {
        Write-Host ""
        Write-Host "Check passed: MySQL, the build and the server all start." -ForegroundColor Green
    } else {
        Write-Step "Opening the window (close it to stop the program)"
        Invoke-Java 'com.bhel.hrm.client.HrmClientMain'
    }
} finally {
    if ($server -and -not $server.HasExited) {
        Write-Step "Stopping the server"
        Stop-Process -Id $server.Id -Force
    }
}
