$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'environment.ps1')
$previousName = $env:APP_ADMIN_USERNAME
$previousPassword = $env:APP_ADMIN_PASSWORD
function Assert-Equal($actual, $expected, $description) {
    if ($actual -cne $expected) { throw "Failed: $description" }
}
try {
    $saved = [System.Management.Automation.PSCredential]::new('saved-admin', (ConvertTo-SecureString 'SavedPassword123!' -AsPlainText -Force))
    $values = @{ APP_ADMIN_USERNAME = 'env-admin'; APP_ADMIN_PASSWORD = 'EnvPassword123!' }
    $env:APP_ADMIN_USERNAME = $null; $env:APP_ADMIN_PASSWORD = $null
    Assert-Equal (Set-WebsiteAdminEnvironment $values $saved) '.env' '.env source'
    Assert-Equal $env:APP_ADMIN_USERNAME 'env-admin' '.env overrides saved username'
    Assert-Equal $env:APP_ADMIN_PASSWORD 'EnvPassword123!' '.env overrides saved password'
    $env:APP_ADMIN_USERNAME = 'process-admin'; $env:APP_ADMIN_PASSWORD = 'ProcessPassword123!'
    Assert-Equal (Set-WebsiteAdminEnvironment $values $saved) 'process environment' 'process source'
    Assert-Equal $env:APP_ADMIN_PASSWORD 'ProcessPassword123!' 'process precedence'
    $env:APP_ADMIN_USERNAME = $null; $env:APP_ADMIN_PASSWORD = $null
    Assert-Equal (Set-WebsiteAdminEnvironment @{} $saved) 'saved launcher settings' 'saved fallback'
    Assert-Equal $env:APP_ADMIN_PASSWORD 'SavedPassword123!' 'saved password fallback'
    $env:APP_ADMIN_USERNAME = 'incomplete'; $env:APP_ADMIN_PASSWORD = $null
    $rejected = $false
    try { Set-WebsiteAdminEnvironment $values $saved | Out-Null } catch { $rejected = $true }
    if (!$rejected) { throw 'Partial process credentials must not be mixed with .env' }
    $env:APP_ADMIN_USERNAME = $null; $env:APP_ADMIN_PASSWORD = $null
    $rejected = $false
    try { Set-WebsiteAdminEnvironment @{ APP_ADMIN_USERNAME = 'incomplete' } $saved | Out-Null } catch { $rejected = $true }
    if (!$rejected) { throw 'Partial .env credentials must not be mixed with saved settings' }
    Write-Output 'Passed: .env/process/saved credential precedence and incomplete-pair rejection.'
} finally {
    $env:APP_ADMIN_USERNAME = $previousName
    $env:APP_ADMIN_PASSWORD = $previousPassword
}
