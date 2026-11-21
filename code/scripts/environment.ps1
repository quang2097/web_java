# Shared .env parsing and website-admin credential precedence. Never logs values.
function Get-ProjectDotEnv {
    param([string]$Path)
    $values = @{}
    if (!(Test-Path -LiteralPath $Path)) { return $values }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') { continue }
        $key = $matches[1]
        $value = $matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$key] = $value
    }
    return $values
}

function Set-WebsiteAdminEnvironment {
    param([hashtable]$DotEnv, [System.Management.Automation.PSCredential]$SavedAdmin)
    $name = [Environment]::GetEnvironmentVariable('APP_ADMIN_USERNAME', 'Process')
    $password = [Environment]::GetEnvironmentVariable('APP_ADMIN_PASSWORD', 'Process')
    $source = 'process environment'
    if (!$name -and !$password) {
        $name = $DotEnv['APP_ADMIN_USERNAME']
        $password = $DotEnv['APP_ADMIN_PASSWORD']
        $source = '.env'
        if (!$name -and !$password -and $SavedAdmin) {
            $name = $SavedAdmin.UserName
            $password = $SavedAdmin.GetNetworkCredential().Password
            $source = 'saved launcher settings'
        }
    }
    if (!$name -or !$password) {
        throw "Set both APP_ADMIN_USERNAME and APP_ADMIN_PASSWORD in $source. Credentials from different sources are not combined."
    }
    [Environment]::SetEnvironmentVariable('APP_ADMIN_USERNAME', $name, 'Process')
    [Environment]::SetEnvironmentVariable('APP_ADMIN_PASSWORD', $password, 'Process')
    return $source
}
