param(
    [string]$Device = "",
    [string]$AdbPath = "adb",
    [string]$Apk = ""
)
$ErrorActionPreference = "Stop"
try {
    $adb = (Get-Command $AdbPath -ErrorAction Stop).Source
    if (-not $Apk) { $Apk = Join-Path (Split-Path $PSScriptRoot -Parent) "QuietTV-0.7.0-dev.apk" }
    if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "APK file not found." }
    $lines = & $adb devices
    if ($LASTEXITCODE -ne 0) { throw "adb devices failed." }
    $online = @($lines | Where-Object { $_ -match "^([^\s]+)\s+device$" } | ForEach-Object { ($_ -split "\s+")[0] })
    if (-not $Device) {
        if ($online.Count -ne 1) { throw "Connect and authorize exactly one TV, or pass -Device IP:PORT." }
        $Device = $online[0]
    }
    if ($online -notcontains $Device) { throw "Selected TV is not connected/authorized." }
    $result = & $adb -s $Device install -r $Apk 2>&1
    $code = $LASTEXITCODE
    $result | Write-Host
    if ($code -ne 0 -or ($result -join " ") -notmatch "\bSuccess\b") { throw "Installation failed. Existing system apps were not changed." }
    $startResult = & $adb -s $Device shell am start -n local.quiettv.home/.MainActivity 2>&1
    $startCode = $LASTEXITCODE
    $startResult | Write-Host
    if ($startCode -ne 0 -or ($startResult -join " ") -match "Error:|Exception") {
        throw "Installed, but could not start. Open Quiet TV from the TV app list."
    }
    Write-Host "Installed. Default launcher and system apps were not changed."
} catch {
    Write-Error $_
    exit 1
}
