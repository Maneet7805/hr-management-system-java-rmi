# Shared helpers for setup.ps1 and run.ps1. Not meant to be run on its own.
# Windows PowerShell 5.1 compatible.

# Programs such as keytool and java write ordinary progress messages to the error stream. Treating those as fatal
# would stop the script halfway, so the scripts check each program's exit code instead.
$ErrorActionPreference = 'Continue'
$script:Root = Split-Path -Parent $PSScriptRoot
Set-Location $script:Root

# The class path: our own classes plus the libraries copied into target\lib (the MySQL driver).
$script:ClassPath = 'target\classes;target\lib\*'

function Write-Step([string]$Text) {
    Write-Host "==> $Text" -ForegroundColor Cyan
}

function Fail([string]$Text) {
    Write-Host ""
    Write-Host "ERROR: $Text" -ForegroundColor Red
    exit 1
}

# Passwords saved by setup live in the Windows user settings, never in a file in the project.
# A terminal that was open before they were saved does not have them yet, so read them from there.
function Import-SavedSettings {
    foreach ($name in 'HRM_DB_PASSWORD', 'HRM_KEYSTORE_PASSWORD', 'HRM_TRUSTSTORE_PASSWORD') {
        if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
            $saved = [Environment]::GetEnvironmentVariable($name, 'User')
            if ($saved) {
                [Environment]::SetEnvironmentVariable($name, $saved, 'Process')
            }
        }
    }
}

function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $attempt = $client.ConnectAsync('127.0.0.1', $Port)
        return ($attempt.Wait(500) -and $client.Connected)
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Wait-ForPort([int]$Port, [int]$Seconds, $Process = $null) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port $Port) { return $true }
        if ($Process -and $Process.HasExited) { return $false }
        Start-Sleep -Milliseconds 400
    }
    return $false
}

function Get-Setting([string]$File, [string]$Key, [string]$Default) {
    if (Test-Path $File) {
        $line = Get-Content $File | Where-Object { $_ -match "^\s*$([regex]::Escape($Key))\s*=" } | Select-Object -First 1
        if ($line) { return ($line -split '=', 2)[1].Trim() }
    }
    return $Default
}

# Changes one key in a settings file and keeps every other line, comments included, as it is.
function Set-Setting([string]$File, [string]$Key, [string]$Value) {
    $pattern = "^\s*$([regex]::Escape($Key))\s*="
    $lines = @(Get-Content $File)
    if ($lines | Where-Object { $_ -match $pattern }) {
        $lines = @($lines | ForEach-Object { if ($_ -match $pattern) { "$Key=$Value" } else { $_ } })
    } else {
        $lines += "$Key=$Value"
    }
    # UTF-8 without a byte-order mark, which is how Java reads the file.
    [System.IO.File]::WriteAllLines((Resolve-Path $File).Path, [string[]]$lines)
}

# A server address goes into the certificate and the settings files, so only a plain name or IP address is allowed.
function Assert-Address([string]$Address) {
    if ($Address -notmatch '^[A-Za-z0-9.:-]+$') { Fail "'$Address' is not a host name or IP address." }
}

# Why the server key files cannot be reused, or $null if they can. They can when all three exist, open with the
# passwords in HRM_KEYSTORE_PASSWORD and HRM_TRUSTSTORE_PASSWORD, hold the very same certificate (files left over
# from different runs would make clients refuse the server), and that certificate lists every requested address.
function Get-KeysProblem([string[]]$Addresses = @()) {
    $files = 'config\server.keystore', 'config\server.cer', 'config\client.truststore'
    $missing = @($files | Where-Object { -not (Test-Path $_) })
    if ($missing.Count -gt 0) { return "$($missing -join ', ') is missing" }
    # keytool needs real paths: on macOS and Linux it would read 'config\server.keystore' as one file name.
    $keystore, $certificate, $truststore = $files | ForEach-Object { (Resolve-Path $_).ProviderPath }

    $listing = & keytool -list -v -keystore $keystore '-storepass:env' 'HRM_KEYSTORE_PASSWORD' 2> $null
    if ($LASTEXITCODE -ne 0) { return "the saved password does not open config\server.keystore" }
    $trusted = & keytool -list -v -keystore $truststore '-storepass:env' 'HRM_TRUSTSTORE_PASSWORD' 2> $null
    if ($LASTEXITCODE -ne 0) { return "the saved password does not open config\client.truststore" }
    $published = & keytool -printcert -file $certificate 2> $null
    if ($LASTEXITCODE -ne 0) { return "config\server.cer cannot be read" }

    $fingerprint = Get-Fingerprints $listing
    if (-not $fingerprint -or (Get-Fingerprints $published) -ne $fingerprint -or (Get-Fingerprints $trusted) -ne $fingerprint) {
        return "server.keystore, server.cer and client.truststore do not hold the same certificate"
    }
    foreach ($address in $Addresses) {
        if (-not (Test-KeysCover $listing @($address))) { return "the certificate does not include $address" }
    }
    return $null
}

# The SHA256 fingerprints in keytool's output, as one piece of text so two listings can be compared.
function Get-Fingerprints($Listing) {
    return (@($Listing | ForEach-Object { if ($_ -match '^\s*SHA256:\s*(\S+)') { $Matches[1] } }) -join ',')
}

# True if keytool's listing of the certificate names every address (it prints "DNSName: x" or "IPAddress: x").
function Test-KeysCover($Listing, [string[]]$Addresses) {
    foreach ($address in $Addresses) {
        $pattern = "^\s*(DNSName|IPAddress):\s*$([regex]::Escape($address))\s*$"
        if (-not ($Listing | Where-Object { $_ -match $pattern })) { return $false }
    }
    return $true
}

function New-RandomPassword {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'.ToCharArray()
    $bytes = New-Object byte[] 24
    $generator = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    $generator.GetBytes($bytes)
    $generator.Dispose()
    return -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}

function Assert-Tools {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Fail "Java was not found. Install JDK 25 and make sure 'java' works in a new terminal." }
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { Fail "Maven was not found. Install Maven and make sure 'mvn' works in a new terminal." }
}

# MySQL is started by hand on this project (no Windows service). The paths can be changed with
# the environment variables HRM_MYSQLD (full path of mysqld.exe) and HRM_MYSQL_DATA (data folder).
function Start-MySql {
    if (Test-Port 3306) {
        Write-Host "MySQL is already running."
        return
    }
    $mysqld = if ($env:HRM_MYSQLD) { $env:HRM_MYSQLD } else { 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe' }
    $data = if ($env:HRM_MYSQL_DATA) { $env:HRM_MYSQL_DATA } else { 'C:\tools\mysql-data' }
    if (-not (Test-Path $mysqld)) { Fail "MySQL was not found at $mysqld. Install MySQL 8.4, or set HRM_MYSQLD to the full path of mysqld.exe." }
    if (-not (Test-Path $data)) { Fail "The MySQL data folder $data does not exist. Set HRM_MYSQL_DATA to your data folder." }
    Write-Step "Starting MySQL"
    Start-Process -FilePath $mysqld -WindowStyle Hidden -ErrorAction Stop -ArgumentList @(
        '--console', "--datadir=$data", '--bind-address=127.0.0.1', '--port=3306', '--mysqlx=OFF') | Out-Null
    if (-not (Wait-ForPort 3306 40)) { Fail "MySQL did not start within 40 seconds." }
}

function Build-Project {
    Write-Step "Compiling the program"
    & mvn -q compile
    if ($LASTEXITCODE -ne 0) { Fail "The build failed. Run 'mvn compile' to see why." }
    # The MySQL driver is copied next to the program once, and again if pom.xml changes.
    $libraries = 'target\lib'
    if (-not (Test-Path $libraries) -or (Get-Item 'pom.xml').LastWriteTime -gt (Get-Item $libraries).LastWriteTime) {
        Write-Step "Copying the libraries"
        & mvn -q dependency:copy-dependencies "-DoutputDirectory=$libraries" "-DincludeScope=runtime"
        if ($LASTEXITCODE -ne 0) { Fail "Could not copy the libraries. Check your internet connection and try again." }
        (Get-Item $libraries).LastWriteTime = Get-Date
    }
}

function Invoke-Java([string]$MainClass, [string[]]$Arguments = @()) {
    & java -cp $script:ClassPath $MainClass @Arguments
}
