# SPDX-License-Identifier: GPL-3.0-or-later
# ADB helpers for manual/semi-automated device testing of Photos.
# Usage:  . .\scripts\device.ps1   then e.g.  Tap-Text "Allow access to photos"; Shot "02_grid"

# Captures go to $env:PHOTOS_SHOTS if set (e.g. outside a synced folder), else test-artifacts/ (git-ignored).
$script:Artifacts = if ($env:PHOTOS_SHOTS) { $env:PHOTOS_SHOTS } else { Join-Path $PSScriptRoot "..\test-artifacts" }
New-Item -ItemType Directory -Force $script:Artifacts | Out-Null

function Get-UiXml {
    adb shell uiautomator dump /data/local/tmp/ui.xml 2>&1 | Out-Null
    $tmp = Join-Path $env:TEMP "photos_ui.xml"
    adb pull /data/local/tmp/ui.xml $tmp 2>&1 | Out-Null
    [xml](Get-Content $tmp -Raw -Encoding UTF8)
}

function Find-Node([string]$Text, [switch]$Contains) {
    $xml = Get-UiXml
    $nodes = $xml.SelectNodes("//node")
    foreach ($n in $nodes) {
        $t = $n.GetAttribute("text"); $d = $n.GetAttribute("content-desc")
        $hit = if ($Contains) { $t -like "*$Text*" -or $d -like "*$Text*" } else { $t -eq $Text -or $d -eq $Text }
        if ($hit) { return $n }
    }
    return $null
}

function Get-Center($node) {
    $b = $node.GetAttribute("bounds")
    if ($b -match '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') {
        return @([int](([int]$matches[1] + [int]$matches[3]) / 2), [int](([int]$matches[2] + [int]$matches[4]) / 2))
    }
}

function Tap-Text([string]$Text, [switch]$Contains, [int]$Wait = 1200) {
    $n = Find-Node $Text -Contains:$Contains
    if ($null -eq $n) { Write-Host "NOT FOUND: $Text"; return $false }
    $c = Get-Center $n
    adb shell input tap $c[0] $c[1]
    Start-Sleep -Milliseconds $Wait
    Write-Host "tapped '$Text' at $($c[0]),$($c[1])"
    return $true
}

function LongPress-Text([string]$Text, [switch]$Contains) {
    $n = Find-Node $Text -Contains:$Contains
    if ($null -eq $n) { Write-Host "NOT FOUND: $Text"; return $false }
    $c = Get-Center $n
    adb shell input swipe $c[0] $c[1] $c[0] $c[1] 800
    Start-Sleep -Milliseconds 800
    return $true
}

function Type-Text([string]$Text) {
    # input text needs spaces escaped as %s
    adb shell input text ($Text -replace ' ', '%s')
    Start-Sleep -Milliseconds 400
}

function Shot([string]$Name) {
    adb shell rm -f /data/local/tmp/shot.png   # never reuse a stale capture
    adb shell screencap -p /data/local/tmp/shot.png
    adb pull /data/local/tmp/shot.png (Join-Path $script:Artifacts "$Name.png") 2>&1 | Out-Null
    Write-Host "saved $Name.png"
}

function Texts {
    $xml = Get-UiXml
    $xml.SelectNodes("//node") | ForEach-Object {
        $t = $_.GetAttribute("text"); $d = $_.GetAttribute("content-desc")
        if ($t -or $d) { "{0} | {1} | {2}" -f $t, $d, $_.GetAttribute("bounds") }
    }
}

# True only when Photos is the focused window (guards taps / typing so input never reaches other apps).
function In-Photos {
    "$(adb shell dumpsys window | Select-String 'mCurrentFocus' | Select-Object -First 1)" -match 'com\.ratiojuris\.github\.photos|io\.github\.ratiojuris\.photos'
}