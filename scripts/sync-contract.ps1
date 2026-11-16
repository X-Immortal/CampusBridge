# 把契约同步进鸿蒙工程。
#
# 用法（在 CampusBridge 目录）：
#   powershell -ExecutionPolicy Bypass -File .\scripts\sync-contract.ps1
#
# 契约的唯一来源是 contract/src/**，app/ 里那四个文件是副本。
# 不要手改 app/ 里的副本：改 contract/ → npm run check → 再跑本脚本。

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$appDir = Join-Path $root 'app'
$entryEts = Join-Path $appDir 'entry\src\main\ets'

if (-not (Test-Path -LiteralPath $entryEts)) {
    Write-Host '找不到鸿蒙工程：' $entryEts
    Write-Host ''
    Write-Host '先在 DevEco Studio 里建 Empty Ability 工程：'
    Write-Host '  路径  CampusBridge\app'
    Write-Host '  API   HarmonyOS 6.0.2 (API 22)'
    Write-Host '  包名  edu.nju.campusbridge2'
    Write-Host ''
    Write-Host '建好后再跑本脚本。'
    exit 1
}

$contractDir = Join-Path $root 'contract'
if (-not (Test-Path -LiteralPath (Join-Path $contractDir 'node_modules'))) {
    Write-Host '提示：contract/node_modules 不存在，先执行 "cd contract; npm install; npm run check"。'
}

$pairs = @(
    @{ src = 'src\protocol\types.ts';     dst = 'protocol\types.ts' },
    @{ src = 'src\protocol\whitelist.ts'; dst = 'protocol\whitelist.ts' },
    @{ src = 'src\transport\events.ts';   dst = 'transport\events.ts' },
    @{ src = 'src\transport\sse.ts';      dst = 'transport\sse.ts' }
)

foreach ($pair in $pairs) {
    $from = Join-Path $contractDir $pair.src
    $to = Join-Path $entryEts $pair.dst
    $targetDir = Split-Path -Parent $to
    if (-not (Test-Path -LiteralPath $targetDir)) {
        New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    }
    Copy-Item -LiteralPath $from -Destination $to -Force
    Write-Host ('  ' + $pair.src + '  ->  ' + $pair.dst)
}

Write-Host ''
Write-Host '契约已同步。app/ 里的这四个文件是副本，别手改；改 contract/ 再跑一次。'
