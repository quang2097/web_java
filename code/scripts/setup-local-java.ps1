$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$toolsPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.tools'
New-Item -ItemType Directory -Force -Path $toolsPath | Out-Null

# Portable toolchain only; does not install system-wide or change system PATH.
$jdkArchive = Join-Path $toolsPath 'jdk.zip'
$mavenArchive = Join-Path $toolsPath 'maven.zip'

Write-Output 'Downloading a portable JDK (approximately 200 MB)...'
& curl.exe -# -fLsS --connect-timeout 30 --max-time 1800 --output $jdkArchive 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse'
if ($LASTEXITCODE -ne 0) { throw 'JDK download failed' }

Write-Output 'Downloading Maven...'
& curl.exe -# -fLsS --max-time 60 --output $mavenArchive 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip'
if ($LASTEXITCODE -ne 0) { throw 'Maven download failed' }

$checksum = (& curl.exe -fLsS --max-time 30 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip.sha512').Trim()
if ($LASTEXITCODE -ne 0) { throw 'Maven checksum download failed' }

if ((Get-FileHash -LiteralPath $mavenArchive -Algorithm SHA512).Hash.ToLowerInvariant() -ne $checksum.ToLowerInvariant()) {
    throw 'Maven archive checksum mismatch'
}

Write-Output 'Extracting archives (this is now much faster)...'
& tar.exe -xf $jdkArchive -C $toolsPath
& tar.exe -xf $mavenArchive -C $toolsPath

Write-Output 'Portable JDK and Maven prepared in .tools'