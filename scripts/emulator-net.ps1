# 给模拟器/真机做反向端口，让设备里的 App 能连到电脑上的 mock(4390) 和后端(8081)。
#
# 用法（在 CampusBridge 目录）：
#   powershell -ExecutionPolicy Bypass -File .\scripts\emulator-net.ps1
#
# 模拟器里的 127.0.0.1 是设备自己，不是你的电脑；hdc rport 把端口"借"给设备。
# 模拟器每次重启都要重跑一次。

$ErrorActionPreference = 'Stop'

$hdc = 'D:\DevEco\DevEco Studio\sdk\default\openharmony\toolchains\hdc.exe'
if (-not (Test-Path -LiteralPath $hdc)) {
    Write-Host '找不到 hdc：' $hdc
    exit 1
}

$targets = & $hdc list targets 2>&1
Write-Host '当前设备：' $targets
if ($targets -match '\[Empty\]' -or $targets -match '^\s*$') {
    Write-Host ''
    Write-Host '没有设备。先把模拟器启动起来（DevEco → Tools → Device Manager → Start），再跑这个脚本。'
    exit 1
}

Write-Host ''
foreach ($port in 4390, 8081) {
    & $hdc rport ("tcp:" + $port) ("tcp:" + $port) 2>&1 | ForEach-Object { Write-Host $_ }
}

Write-Host ''
Write-Host '当前转发规则：'
& $hdc fport ls 2>&1 | ForEach-Object { Write-Host $_ }
Write-Host ''
Write-Host 'App 里直接写 http://127.0.0.1:4390（mock）和 http://127.0.0.1:8081（后端）。'
