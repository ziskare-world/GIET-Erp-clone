param(
    [string]$DeviceId = "",
    [switch]$SkipBuild,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleWrapper = Join-Path $scriptRoot "gradlew.bat"
$apkPath = Join-Path $scriptRoot "app\build\outputs\apk\debug\app-debug.apk"
$packageName = "com.example.gieterp"
$launchActivity = ".MainActivity"

function Get-LocalPropertiesSdkDir {
    $localPropertiesPath = Join-Path $scriptRoot "local.properties"
    if (-not (Test-Path $localPropertiesPath)) {
        return $null
    }

    $sdkLine = Get-Content $localPropertiesPath | Where-Object { $_ -like "sdk.dir=*" } | Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($sdkLine)) {
        return $null
    }

    $sdkDir = $sdkLine.Substring(8).Trim()
    $sdkDir = $sdkDir -replace "\\\\", "\"
    $sdkDir = $sdkDir -replace "\\:", ":"
    return $sdkDir
}

function Resolve-AdbCommand {
    $candidates = @()

    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME)) {
        $candidates += Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT)) {
        $candidates += Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe"
    }

    $sdkDir = Get-LocalPropertiesSdkDir
    if (-not [string]::IsNullOrWhiteSpace($sdkDir)) {
        $candidates += Join-Path $sdkDir "platform-tools\adb.exe"
    }

    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    if (Get-Command "adb" -ErrorAction SilentlyContinue) {
        return "adb"
    }

    return $null
}

function Require-File {
    param([string]$Path)
    if (-not (Test-Path $Path)) {
        throw "Required file not found: $Path"
    }
}

function Invoke-Step {
    param(
        [string]$Command,
        [string[]]$Arguments
    )

    if ($DryRun) {
        Write-Host "[dry-run] $Command $($Arguments -join ' ')"
        return
    }

    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $Command $($Arguments -join ' ')"
    }
}

function Get-ConnectedDevices {
    param([string]$AdbCommand)

    $lines = & $AdbCommand devices
    $devices = @()
    foreach ($line in $lines) {
        if ($line -match "^\s*([^\s]+)\s+device(?:\s+.*)?$") {
            $devices += $matches[1]
        }
    }
    return ,$devices
}

Push-Location $scriptRoot
try {
    $adbCommand = Resolve-AdbCommand
    if ([string]::IsNullOrWhiteSpace($adbCommand)) {
        throw "adb was not found. Install Android platform-tools or set ANDROID_HOME/ANDROID_SDK_ROOT."
    }
    Require-File -Path $gradleWrapper

    if ($DryRun) {
        if ([string]::IsNullOrWhiteSpace($DeviceId)) {
            $DeviceId = "device-id"
        }
        $connectedDevices = @($DeviceId)
    } else {
        $connectedDevices = Get-ConnectedDevices -AdbCommand $adbCommand
    }
    if ([string]::IsNullOrWhiteSpace($DeviceId)) {
        if ($connectedDevices.Count -eq 0 -and -not $DryRun) {
            throw "No Android device detected. Connect a device and enable USB debugging."
        }
        if ($connectedDevices.Count -eq 0 -and $DryRun) {
            $DeviceId = "device-id"
        } else {
            $DeviceId = $connectedDevices[0]
        }
        if ($connectedDevices.Count -gt 1) {
            Write-Host "Multiple devices detected. Using the first device: $DeviceId"
            Write-Host "To pick another one: .\run-on-mobile.ps1 -DeviceId <device-id>"
        }
    } elseif ($connectedDevices -notcontains $DeviceId -and -not $DryRun) {
        throw "Device '$DeviceId' is not connected. Connected devices: $($connectedDevices -join ', ')"
    }

    if (-not $SkipBuild) {
        Invoke-Step -Command $gradleWrapper -Arguments @(":app:assembleDebug")
    }

    if (-not (Test-Path $apkPath) -and -not $DryRun) {
        throw "Debug APK not found at $apkPath. Build failed or output path changed."
    }

    Invoke-Step -Command $adbCommand -Arguments @("-s", $DeviceId, "install", "-r", $apkPath)
    Invoke-Step -Command $adbCommand -Arguments @("-s", $DeviceId, "shell", "am", "start", "-n", "$packageName/$launchActivity")

    Write-Host "App installed and launched on device: $DeviceId"
} finally {
    Pop-Location
}
