# One-time setup: key passwords, server keys, settings files, database tables, first HR account.
# Run it with setup.bat (double-click) or:  powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
#
#   -ServerAddress 192.168.1.20   let other computers connect: the IP address (or name) they use to reach this one.
#                                 It is added to the server certificate and set as rmi.hostname. Several addresses
#                                 can be given, separated by commas; the first one is the one clients are sent to.
#   -NewKeys                      make new keys even if the existing ones still work
#   -NoSaveSettings               do not save passwords in the Windows user settings (for testing)
#   -SkipSeed                     do not offer to create an HR account
param(
    [string[]]$ServerAddress = @(),
    [switch]$NewKeys,
    [switch]$NoSaveSettings,
    [switch]$SkipSeed
)

. "$PSScriptRoot\common.ps1"

Write-Host ""
Write-Host "BHEL HR Management System: one-time setup" -ForegroundColor Green
Write-Host ""

Assert-Tools
Import-SavedSettings
# setup.bat passes "a,b" as one piece of text, so split it here.
$ServerAddress = @($ServerAddress | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
foreach ($address in $ServerAddress) { Assert-Address $address }
# A password made up here and not saved could never be typed again, so the keys would be unusable.
if ($NoSaveSettings -and (-not $env:HRM_KEYSTORE_PASSWORD -or -not $env:HRM_TRUSTSTORE_PASSWORD -or
        $env:HRM_KEYSTORE_PASSWORD -eq $env:HRM_TRUSTSTORE_PASSWORD)) {
    Fail "With -NoSaveSettings, first set HRM_KEYSTORE_PASSWORD and a different HRM_TRUSTSTORE_PASSWORD yourself."
}

# 1. The database password (set up earlier, outside this script, because it belongs to the MySQL account).
if (-not $env:HRM_DB_PASSWORD) {
    Fail ("The database password is not set. Create the MySQL user 'hrm_app' with access to the 'hrm' database, " +
          "then save its password with:  setx HRM_DB_PASSWORD ""the-password""  and open a new terminal.")
}

# 2. The key passwords and the keys. The client truststore gets its own password, so a client computer never
#    needs the password that protects the server's private key.
$newPassword = $false
if (-not $env:HRM_KEYSTORE_PASSWORD) {
    Write-Step "Choosing a password for the server keys"
    $env:HRM_KEYSTORE_PASSWORD = New-RandomPassword
    $newPassword = $true
}
if (-not $env:HRM_TRUSTSTORE_PASSWORD -or $env:HRM_TRUSTSTORE_PASSWORD -eq $env:HRM_KEYSTORE_PASSWORD) {
    Write-Step "Choosing a separate password for the client truststore"
    $env:HRM_TRUSTSTORE_PASSWORD = New-RandomPassword
    $newPassword = $true
}

$problem = if ($newPassword) { 'new passwords were chosen' } elseif ($NewKeys) { '-NewKeys was given' }
           else { Get-KeysProblem $ServerAddress }
if (-not $problem) {
    Write-Host "The keys already exist, belong together, open with the saved passwords and cover the requested addresses."
} else {
    # A running server keeps the keys it loaded when it started. Replacing the files under it would leave it showing
    # clients the old certificate while they are told to trust the new one.
    $serverPort = [int](Get-Setting 'config\server.properties' 'rmi.port' '1099')
    if (Test-Port $serverPort) {
        Fail ("New keys are needed ($problem), but something is listening on port $serverPort, probably the HR " +
              "server. Close the program (the run.bat window) and run setup again.")
    }
    $keyFiles = 'config\server.keystore', 'config\server.cer', 'config\client.truststore'
    if ($keyFiles | Where-Object { Test-Path $_ }) {
        Write-Host "Making new keys: $problem." -ForegroundColor Yellow
        Write-Host "Any client computer must import the new config\server.cer again." -ForegroundColor Yellow
        Remove-Item $keyFiles -ErrorAction SilentlyContinue
    }
    Write-Step "Creating the keys"
    & cmd /c 'scripts\generate-keys.bat' @ServerAddress
    if ($LASTEXITCODE -ne 0) { Fail "Could not create the keys." }
}

if (-not $NoSaveSettings) {
    [Environment]::SetEnvironmentVariable('HRM_KEYSTORE_PASSWORD', $env:HRM_KEYSTORE_PASSWORD, 'User')
    [Environment]::SetEnvironmentVariable('HRM_TRUSTSTORE_PASSWORD', $env:HRM_TRUSTSTORE_PASSWORD, 'User')
    Write-Host "The key passwords were saved in your Windows user settings (not in any file of the project)."
}

# 3. The settings files. Existing ones are never overwritten.
foreach ($name in 'server', 'client') {
    $target = "config\$name.properties"
    if (-not (Test-Path $target)) {
        Copy-Item "config\$name.example.properties" $target -ErrorAction Stop
        Write-Host "Created $target"
    }
}
# The address other computers use goes into every stub the server hands out, so it is set only when asked for.
if ($ServerAddress.Count -gt 0) {
    Set-Setting 'config\server.properties' 'rmi.hostname' $ServerAddress[0]
    Write-Host "Set rmi.hostname=$($ServerAddress[0]) in config\server.properties"
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
$advertised = Get-Setting 'config\server.properties' 'rmi.hostname' 'localhost'
if ($advertised -notmatch '^(localhost|127\..*|::1)$') {
    $ports = "$(Get-Setting 'config\server.properties' 'rmi.port' '1099') and $(Get-Setting 'config\server.properties' 'rmi.objectPort' '1100')"
    Write-Host "Other computers connect to this one at $advertised. On each of them:" -ForegroundColor Green
    Write-Host "  1. Copy config\server.cer there and run setup-client.bat. Check it shows this same fingerprint:"
    & keytool -printcert -file 'config\server.cer' | Select-String 'SHA256'
    Write-Host "  2. On THIS computer, allow TCP ports $ports through Windows Firewall (see docs\two-computer-setup.md)."
} else {
    $addresses = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notmatch '^(127\.|169\.254\.)' } | ForEach-Object { $_.IPAddress })
    if ($addresses.Count -gt 0) {
        Write-Host "Only this computer can connect. To let other computers connect, run setup again with an address, for example:"
        Write-Host "  setup.bat -ServerAddress $($addresses[0])      (this computer's addresses: $($addresses -join ', '))"
    }
}
Write-Host ""
Write-Host "Setup is finished. Start the program with run.bat" -ForegroundColor Green
