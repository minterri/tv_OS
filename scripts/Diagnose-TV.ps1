param(
    [string]$Device = "",
    [string]$AdbPath = "adb"
)
$ErrorActionPreference = "Stop"
try {
    $adb = (Get-Command $AdbPath -ErrorAction Stop).Source
    $lines = & $adb devices
    if ($LASTEXITCODE -ne 0) { throw "adb devices failed." }
    $online = @($lines | Where-Object { $_ -match "^([^\s]+)\s+device$" } | ForEach-Object { ($_ -split "\s+")[0] })
    if (-not $Device) {
        if ($online.Count -ne 1) { throw "Connect and authorize exactly one TV, or pass -Device IP:PORT. Run adb devices." }
        $Device = $online[0]
    }
    if ($online -notcontains $Device) { throw "Selected TV is not connected/authorized. Run adb devices." }
    $outDir = Join-Path (Split-Path $PSScriptRoot -Parent) ("diagnostics-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
    New-Item -ItemType Directory -Path $outDir | Out-Null
    function Save-Adb([string]$Name, [string[]]$CommandArgs) {
        $savedPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $output = & $adb -s $Device @CommandArgs 2>&1
            $exit = $LASTEXITCODE
        } finally { $ErrorActionPreference = $savedPreference }
        @("Exit code: $exit", $output) | Out-File (Join-Path $outDir $Name) -Encoding utf8
    }
    # Read-only. No logcat, account data, serial numbers, Wi-Fi names, browsing history or full getprop dump.
    $properties = @("ro.product.manufacturer", "ro.product.model", "ro.product.board",
        "ro.hardware", "ro.build.version.release", "ro.build.version.sdk",
        "ro.build.version.security_patch", "ro.build.display.id")
    $values = foreach ($property in $properties) {
        $value = & $adb -s $Device shell getprop $property
        "$property=$value"
    }
    $values | Out-File (Join-Path $outDir "device.txt") -Encoding utf8
    Save-Adb "packages-system.txt" @("shell","pm","list","packages","-s")
    Save-Adb "packages-user.txt" @("shell","pm","list","packages","-3")
    Save-Adb "packages-disabled.txt" @("shell","pm","list","packages","-d")
    Save-Adb "home.txt" @("shell","cmd","package","resolve-activity","--brief","-a","android.intent.action.MAIN","-c","android.intent.category.HOME")
    Save-Adb "storage.txt" @("shell","df","-h","/data")
    Save-Adb "memory.txt" @("shell","cat","/proc/meminfo")
    Write-Host "Read-only report saved to: $outDir"
    Write-Host "Review before sharing: installed app names can reveal personal preferences."
} catch {
    Write-Error $_
    exit 1
}
