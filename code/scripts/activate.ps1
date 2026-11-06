# Dot-source this script to enable the portable toolchain in an existing terminal.
$projectPath = Split-Path $PSScriptRoot -Parent
$jdk = Get-ChildItem -LiteralPath (Join-Path $projectPath '.tools') -Directory -Filter 'jdk-*' | Where-Object { Test-Path (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1
$mavenBin = Join-Path $projectPath '.tools/apache-maven-3.9.11/bin'
if (!$jdk -or !(Test-Path (Join-Path $mavenBin 'mvn.cmd'))) {
    throw 'Portable Java or Maven is missing. Run scripts/setup-local-java.ps1 first.'
}
$env:JAVA_HOME = $jdk.FullName
$env:PATH = "$($jdk.FullName)\bin;$mavenBin;$env:PATH"
