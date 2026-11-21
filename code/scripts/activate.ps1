# Dot-source this script to enable the portable toolchain (or fall back to installed JDK 17+ and Maven).
$projectPath = Split-Path $PSScriptRoot -Parent
$toolsDir = Join-Path $projectPath '.tools'
$jdk = if (Test-Path -LiteralPath $toolsDir) {
    Get-ChildItem -LiteralPath $toolsDir -Directory -Filter 'jdk-*' | Where-Object { Test-Path (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1
}
$mavenBin = Join-Path $projectPath '.tools/apache-maven-3.9.11/bin'
if ($jdk -and (Test-Path (Join-Path $mavenBin 'mvn.cmd'))) {
    $env:JAVA_HOME = $jdk.FullName
    $env:PATH = "$($jdk.FullName)\bin;$mavenBin;$env:PATH"
} elseif (!(Get-Command java.exe -ErrorAction SilentlyContinue) -or !(Get-Command mvn.cmd -ErrorAction SilentlyContinue)) {
    throw 'Portable Java/Maven is missing and system Java/Maven was not found on PATH. Run scripts/setup-local-java.ps1 first.'
}
