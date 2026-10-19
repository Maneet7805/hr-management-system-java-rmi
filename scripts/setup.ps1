# One-time setup: key password, development keys, settings files, database tables, first HR account.
# Run it with setup.bat (double-click) or:  powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
#
#   -NoSaveSettings   do not save passwords in the Windows user settings (for testing)
#   -SkipSeed         do not offer to create an HR account
param(
    [switch]$NoSaveSettings,
    [switch]$SkipSeed
)

. "$PSScriptRoot\common.ps1"

function New-RandomPassword {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'.ToCharArray()
    $bytes = New-Object byte[] 24
    $generator = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    $generator.GetBytes($bytes)
    $generator.Dispose()
    return -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}

Write-Host ""
Write-Host "BHEL HR Management System: one-time setup" -ForegroundColor Green
Write-Host ""

Assert-Tools
Import-SavedSettings

# 1. The database password (set up earlier, outside this script, because it belongs to the MySQL account).
if (-not $env:HRM_DB_PASSWORD) {
    Fail ("The database password is not set. Create the MySQL user 'hrm_app' with access to the 'hrm' database, " +
          "then save its password with:  setx HRM_DB_PASSWORD ""the-password""  and open a new terminal.")
}

# 2. The key password and the development keys.
$newPassword = $false
if (-not $env:HRM_KEYSTORE_PASSWORD) {
    Write-Step "Choosing a password for the development keys"
    $env:HRM_KEYSTORE_PASSWORD = New-RandomPassword
    $newPassword = $true
}
$env:HRM_TRUSTSTORE_PASSWORD = $env:HRM_KEYSTORE_PASSWORD   # the key script uses one password for both

$keysWork = $false
if ((Test-Path 'config\server.keystore') -and (Test-Path 'config\client.truststore') -and -not $newPassword) {
    & keytool -list -keystore 'config\server.keystore' -storepass $env:HRM_KEYSTORE_PASSWORD *> $null
    $keysWork = ($LASTEXITCODE -eq 0)
}
if ($keysWork) {
    Write-Host "The development keys already exist and the saved password opens them."
} else {
    if ((Test-Path 'config\server.keystore') -or (Test-Path 'config\client.truststore')) {
        Write-Host "The existing development keys cannot be opened with the saved password, so new ones are made." -ForegroundColor Yellow
        Remove-Item 'config\server.keystore', 'config\client.truststore' -ErrorAction SilentlyContinue
    }
    Write-Step "Creating the development keys"
    & cmd /c 'scripts\generate-keys.bat'
    if ($LASTEXITCODE -ne 0) { Fail "Could not create the keys." }
}

if (-not $NoSaveSettings) {
    [Environment]::SetEnvironmentVariable('HRM_KEYSTORE_PASSWORD', $env:HRM_KEYSTORE_PASSWORD, 'User')
    [Environment]::SetEnvironmentVariable('HRM_TRUSTSTORE_PASSWORD', $env:HRM_TRUSTSTORE_PASSWORD, 'User')
    Write-Host "The key password was saved in your Windows user settings (not in any file of the project)."
}

# 3. The settings files. Existing ones are never overwritten.
foreach ($name in 'server', 'client') {
    $target = "config\$name.properties"
    if (-not (Test-Path $target)) {
        Copy-Item "config\$name.example.properties" $target -ErrorAction Stop
        Write-Host "Created $target"
    }
}

# 4. Build, database tables, first HR account.
Build-Project
Start-MySql
Write-Step "Creating the database tables"
Invoke-Java 'com.bhel.hrm.server.db.DatabaseSetupMain'
if ($LASTEXITCODE -ne 0) { Fail "Could not create the database tables." }

if (-not $SkipSeed) {
    Write-Host ""
    $answer = Read-Host "Create an HR account now? You need one to log in the first time. [Y/n]"
    if ($answer -eq '' -or $answer -match '^[Yy]') {
        Invoke-Java 'com.bhel.hrm.server.SeedHrMain'
        if ($LASTEXITCODE -ne 0) { Write-Host "No account was created. Run setup again to retry." -ForegroundColor Yellow }
    }
}

Write-Host ""
Write-Host "Setup is finished. Start the program with run.bat" -ForegroundColor Green
