Get-WindowsOptionalFeature -Online -FeatureName VirtualMachinePlatform,Microsoft-Windows-Subsystem-Linux,Microsoft-Hyper-V-All |
    Select-Object FeatureName, State |
    Format-Table -AutoSize |
    Out-String |
    Set-Content -LiteralPath (Join-Path (Split-Path -Parent $PSScriptRoot) "logs\wsl-features-elevated.txt") -Encoding UTF8
