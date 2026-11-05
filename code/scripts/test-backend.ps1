$ErrorActionPreference = 'Stop'
$projectPath = Split-Path $PSScriptRoot -Parent
$toolsPath = Join-Path $projectPath '.tools'
$jdk = Get-ChildItem -LiteralPath $toolsPath -Directory -Filter 'jdk-*' | Select-Object -First 1
$maven = Join-Path $toolsPath 'apache-maven-3.9.11/bin/mvn.cmd'
if (!$jdk -or !(Test-Path -LiteralPath $maven)) { throw 'Run scripts/setup-local-java.ps1 first, or use your installed JDK 17+ and Maven.' }
$env:JAVA_HOME = $jdk.FullName
$repositoryPath = Join-Path $toolsPath 'm2'
Push-Location (Join-Path $projectPath 'backend')
try {
    & $maven "-Dmaven.repo.local=$repositoryPath" --batch-mode test
    if ($LASTEXITCODE -ne 0) { throw "Maven tests failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }
