param([switch]$ConfigureDatabase, [switch]$UseDocker, [switch]$NoDialogs)

$ErrorActionPreference = 'Stop'
$projectPath = Split-Path $PSScriptRoot -Parent
Add-Type -AssemblyName System.Windows.Forms

function Read-Login($title, $message, $username) {
    $isDatabase = $title -match 'PostgreSQL'
    $form = New-Object System.Windows.Forms.Form
    $form.Text = $title
    $form.ClientSize = New-Object System.Drawing.Size(590, 290)
    $form.StartPosition = 'CenterScreen'
    $form.FormBorderStyle = 'FixedDialog'
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false
    $description = New-Object System.Windows.Forms.Label
    $description.Location = New-Object System.Drawing.Point(16, 12)
    $description.Size = New-Object System.Drawing.Size(558, 115)
    if ($isDatabase) {
        $description.Text = "DATABASE CONNECTION - used by the server, before the website opens.`r`nEnter an EXISTING PostgreSQL role and its server password.`r`nExamples of role names: supermarket (project setup), postgres (installer admin).`r`nUse the same server login that works in pgAdmin. The pgAdmin master password only unlocks saved passwords; it is not this login.`r`nDatabase: supermarket on localhost:5432 (unless DB_URL is set)."
    } else {
        $description.Text = "WEBSITE LOGIN - used on the Supermarket sign-in page.`r`nChoose a website username and password. This is separate from PostgreSQL.`r`nUsername: 3-64 letters, digits, dot, underscore or hyphen.`r`nPassword: 12-72 printable ASCII characters.`r`nExisting website accounts keep their original password."
    }
    $form.Controls.Add($description)
    $nameLabel = New-Object System.Windows.Forms.Label
    $nameLabel.Location = New-Object System.Drawing.Point(16, 139)
    $nameLabel.Size = New-Object System.Drawing.Size(225, 24)
    $nameLabel.Text = if ($isDatabase) { 'PostgreSQL role / username' } else { 'Website username' }
    $nameBox = New-Object System.Windows.Forms.TextBox
    $nameBox.Location = New-Object System.Drawing.Point(245, 136)
    $nameBox.Size = New-Object System.Drawing.Size(325, 24)
    $nameBox.Text = $username
    $passwordLabel = New-Object System.Windows.Forms.Label
    $passwordLabel.Location = New-Object System.Drawing.Point(16, 179)
    $passwordLabel.Size = New-Object System.Drawing.Size(225, 24)
    $passwordLabel.Text = if ($isDatabase) { 'PostgreSQL server password' } else { 'Website password (12-72 characters)' }
    $passwordBox = New-Object System.Windows.Forms.TextBox
    $passwordBox.Location = New-Object System.Drawing.Point(245, 176)
    $passwordBox.Size = New-Object System.Drawing.Size(325, 24)
    $passwordBox.UseSystemPasswordChar = $true
    $ok = New-Object System.Windows.Forms.Button
    $ok.Text = 'Continue'
    $ok.Location = New-Object System.Drawing.Point(360, 238)
    $ok.Size = New-Object System.Drawing.Size(100, 30)
    $ok.DialogResult = 'OK'
    $cancel = New-Object System.Windows.Forms.Button
    $cancel.Text = 'Cancel'
    $cancel.Location = New-Object System.Drawing.Point(470, 238)
    $cancel.Size = New-Object System.Drawing.Size(100, 30)
    $cancel.DialogResult = 'Cancel'
    $form.Controls.AddRange(@($nameLabel, $nameBox, $passwordLabel, $passwordBox, $ok, $cancel))
    $form.AcceptButton = $ok
    $form.CancelButton = $cancel
    try {
        if ($form.ShowDialog() -ne 'OK') { throw 'Setup cancelled.' }
        if ([string]::IsNullOrWhiteSpace($nameBox.Text) -or !$passwordBox.Text) { throw 'Both a username and password are required.' }
        return [System.Management.Automation.PSCredential]::new($nameBox.Text.Trim(), (ConvertTo-SecureString $passwordBox.Text -AsPlainText -Force))
    } finally { $form.Dispose() }
}

try {
    . (Join-Path $PSScriptRoot 'activate.ps1')
    $logDirectory = Join-Path $projectPath '.tools/logs'
    New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
    $logPath = Join-Path $logDirectory ("startup-{0}-{1}.log" -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'), $PID)
    "Supermarket launch: $(Get-Date -Format o)" | Out-File -LiteralPath $logPath -Encoding Unicode
    $logPath | Set-Content -LiteralPath (Join-Path $projectPath '.tools/latest-startup.txt') -Encoding UTF8
    Write-Host "Startup log: $logPath"
    $configPath = Join-Path $projectPath '.tools/launcher-config.xml'
    if (Test-Path -LiteralPath $configPath) {
        $config = Import-Clixml -LiteralPath $configPath
        if ($ConfigureDatabase) {
            $config.Database = Read-Login 'Update PostgreSQL connection' 'Enter the existing PostgreSQL login for database supermarket. This updates the saved connection only; it does not change the database password.' $config.Database.UserName
            $config | Export-Clixml -LiteralPath $configPath
        }
    } else {
        $database = Read-Login 'PostgreSQL connection' 'Enter your existing local PostgreSQL username and password. Database: supermarket, port: 5432.' 'supermarket'
        $admin = Read-Login 'Initial administrator' 'Choose an admin username (3-64 letters/digits/._-) and password (12-72 printable ASCII characters). Existing accounts are never reset.' 'admin'
        if ($admin.UserName -notmatch '^[A-Za-z0-9._-]{3,64}$' -or $admin.GetNetworkCredential().Password -notmatch '^[\x20-\x7E]{12,72}$') {
            throw 'Invalid administrator username or password. Start again and follow the credential requirements.'
        }
        $bytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $config = [PSCustomObject]@{
            Database = $database
            Admin = $admin
            JwtSecret = (ConvertTo-SecureString ([Convert]::ToBase64String($bytes)) -AsPlainText -Force)
        }
        $config | Export-Clixml -LiteralPath $configPath
    }
    $env:DB_USER = $config.Database.UserName
    $env:DB_PASSWORD = $config.Database.GetNetworkCredential().Password
    if (!$env:DB_URL) { $env:DB_URL = 'jdbc:postgresql://localhost:5432/supermarket' }
    $env:APP_ADMIN_USERNAME = $config.Admin.UserName
    $env:APP_ADMIN_PASSWORD = $config.Admin.GetNetworkCredential().Password
    $env:JWT_SECRET = ([System.Net.NetworkCredential]::new('', $config.JwtSecret)).Password

    $existingWebsite = $null
    try { $existingWebsite = Invoke-WebRequest -UseBasicParsing 'http://localhost:8080' -TimeoutSec 3 } catch { }
    if ($existingWebsite -and $existingWebsite.Content -match '<title>[^<]*Supermarket</title>') {
        $message = 'Supermarket is already running at http://localhost:8080. No second server was started.'
        Write-Host $message
        $message | Out-File -LiteralPath $logPath -Append -Encoding Unicode
        return
    }
    if ($existingWebsite) { throw 'Another website is already using port 8080. Close that server before starting Supermarket.' }

    # This machine uses native PostgreSQL. Docker is an explicit alternative.
    if ($UseDocker) {
        if (!(Get-Command docker.exe -ErrorAction SilentlyContinue)) { throw 'Docker is not installed. Use Start-Supermarket.cmd for local PostgreSQL.' }
            Push-Location $projectPath
            try {
                & docker.exe compose up -d --wait
                if ($LASTEXITCODE -ne 0) { throw 'Database startup failed. Check that Docker Desktop is running.' }
            } finally { Pop-Location }
    }
    if (!(Get-Command npm.cmd -ErrorAction SilentlyContinue)) { throw 'Install Node.js with npm, then start the launcher again.' }
    Push-Location (Join-Path $projectPath 'frontend')
    try {
        if (!(Test-Path 'node_modules')) {
            & npm.cmd ci 2>&1 | Tee-Object -FilePath $logPath -Append
            if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
        }
        & npm.cmd run build 2>&1 | Tee-Object -FilePath $logPath -Append
        if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
    } finally { Pop-Location }

    Write-Host 'Starting Supermarket. Open http://localhost:8080 once Spring reports Started.'
    Write-Host 'Keep this window open while using the app. Press Ctrl+C to stop.'
    Push-Location (Join-Path $projectPath 'backend')
    try {
        # Tee native output explicitly: Windows PowerShell transcripts can omit it.
        $previousErrorPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & mvn.cmd "-Dmaven.repo.local=$(Join-Path $projectPath '.tools/m2')" spring-boot:run 2>&1 | Tee-Object -FilePath $logPath -Append
            $appExitCode = $LASTEXITCODE
        } finally { $ErrorActionPreference = $previousErrorPreference }
        if ($appExitCode -ne 0) {
            if (Select-String -LiteralPath $logPath -Pattern 'password authentication failed' -Quiet) {
                throw "PostgreSQL rejected the saved database login. Double-click Configure-Database.cmd and enter the correct existing PostgreSQL username and password. Log: $logPath"
            }
            throw "Application exited with code $appExitCode. Log: $logPath"
        }
    } finally { Pop-Location }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    if ($logPath -and (Test-Path -LiteralPath $logPath)) {
        $_.Exception.Message | Out-File -LiteralPath $logPath -Append -Encoding Unicode
    }
    if (!$NoDialogs) { [System.Windows.Forms.MessageBox]::Show($_.Exception.Message, 'Supermarket could not start', 'OK', 'Error') | Out-Null }
    exit 1
}
