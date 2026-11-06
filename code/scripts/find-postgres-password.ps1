$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
$projectPath = Split-Path $PSScriptRoot -Parent

try {
    $matchesFound = @()
    $configPath = Join-Path $projectPath '.tools/launcher-config.xml'
    if (Test-Path -LiteralPath $configPath) {
        $config = Import-Clixml -LiteralPath $configPath
        if ($config.Database.UserName -ceq 'postgres') {
            $matchesFound += [PSCustomObject]@{
                Source = 'Supermarket launcher saved login (postgres)'
                Password = $config.Database.GetNetworkCredential().Password
            }
        }
    }

    # Only inspect the current user's explicit PostgreSQL password file.
    $passwordFile = if ($env:PGPASSFILE) { $env:PGPASSFILE } else { Join-Path $env:APPDATA 'postgresql/pgpass.conf' }
    if (Test-Path -LiteralPath $passwordFile -PathType Leaf) {
        foreach ($line in [System.IO.File]::ReadAllLines($passwordFile)) {
            if (!$line -or $line.StartsWith('#')) { continue }
            # PostgreSQL permits backslash-escaped colons and backslashes.
            $fields = @()
            $field = ''
            for ($i = 0; $i -lt $line.Length; $i++) {
                $character = $line[$i]
                if ($character -eq '\' -and $i + 1 -lt $line.Length) {
                    $i++
                    $field += $line[$i]
                } elseif ($character -eq ':' -and $fields.Count -lt 4) {
                    $fields += $field
                    $field = ''
                } else { $field += $character }
            }
            $fields += $field
            if ($fields.Count -eq 5 -and $fields[3] -ceq 'postgres' -and
                $fields[0] -in @('localhost', '127.0.0.1', '::1', '*') -and
                $fields[1] -in @('5432', '*')) {
                $matchesFound += [PSCustomObject]@{
                    Source = "PostgreSQL password file: host=$($fields[0]), port=$($fields[1]), database=$($fields[2])"
                    Password = $fields[4]
                }
            }
        }
    }

    if (!$matchesFound.Count) {
        [System.Windows.Forms.MessageBox]::Show('No saved password for the postgres account was found in the Supermarket launcher or your PostgreSQL password file. PostgreSQL stores password hashes, which cannot be read back as the original password. You need a database administrator to reset an unknown password. No settings were changed.', 'No saved postgres password', 'OK', 'Information') | Out-Null
        exit 0
    }

    foreach ($entry in $matchesFound) {
        $form = New-Object System.Windows.Forms.Form
        $form.Text = 'Saved postgres password'
        $form.ClientSize = New-Object System.Drawing.Size(600, 210)
        $form.StartPosition = 'CenterScreen'
        $form.FormBorderStyle = 'FixedDialog'
        $form.MaximizeBox = $false
        $label = New-Object System.Windows.Forms.Label
        $label.Location = New-Object System.Drawing.Point(16, 12)
        $label.Size = New-Object System.Drawing.Size(568, 70)
        $label.Text = "Username: postgres`r`n$($entry.Source)`r`nThis is a saved password; it has not been checked against the server."
        $box = New-Object System.Windows.Forms.TextBox
        $box.Location = New-Object System.Drawing.Point(16, 90)
        $box.Size = New-Object System.Drawing.Size(568, 25)
        $box.ReadOnly = $true
        $box.UseSystemPasswordChar = $true
        $box.Text = $entry.Password
        $reveal = New-Object System.Windows.Forms.CheckBox
        $reveal.Text = 'Show password'
        $reveal.Location = New-Object System.Drawing.Point(16, 127)
        $reveal.Size = New-Object System.Drawing.Size(200, 25)
        $reveal.Add_CheckedChanged({ $box.UseSystemPasswordChar = !$reveal.Checked })
        $close = New-Object System.Windows.Forms.Button
        $close.Text = 'Close'
        $close.Location = New-Object System.Drawing.Point(484, 165)
        $close.DialogResult = 'OK'
        $form.Controls.AddRange(@($label, $box, $reveal, $close))
        $form.AcceptButton = $close
        try { $form.ShowDialog() | Out-Null } finally { $box.Clear(); $form.Dispose() }
    }
} catch {
    [System.Windows.Forms.MessageBox]::Show('Could not read the saved credentials. Run this script using the same Windows account that saved them. No settings were changed.', 'Password lookup failed', 'OK', 'Error') | Out-Null
    exit 1
}
