$ErrorActionPreference = "Stop"
$RepoDir = Join-Path $PSScriptRoot "kdeconnect-android"

if (-not (Test-Path $RepoDir)) {
    git clone https://github.com/KDE/kdeconnect-android.git $RepoDir
}

python (Join-Path $PSScriptRoot "apply_linkdrop_customization.py") $RepoDir --build

Write-Host ""
Write-Host "Build finished. APK folder:"
Write-Host (Join-Path $RepoDir "build\outputs\apk\debug")
