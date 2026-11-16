# 命令行编译鸿蒙工程（不用打开 DevEco，也能验证 ArkTS 编不编得过）
#
# 用法（在 CampusBridge 目录）：
#   powershell -ExecutionPolicy Bypass -File .\scripts\build-app.ps1
#
# 前提：DevEco Studio 装在 D:\DevEco\DevEco Studio；装了 HarmonyOS 6.0.2 (API 22) SDK。
# 注意：工程路径必须全英文，否则 hvigor 报 Invalid project path。

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$appDir = Join-Path $root 'app'
$deveco = 'D:\DevEco\DevEco Studio'

if (-not (Test-Path -LiteralPath $appDir)) {
    Write-Host '找不到 app/ 工程目录：' $appDir
    exit 1
}
if (-not (Test-Path -LiteralPath $deveco)) {
    Write-Host '找不到 DevEco Studio：' $deveco
    Write-Host '改这个脚本里的 $deveco 变量，指向你机器上的安装位置。'
    exit 1
}

$env:DEVECO_SDK_HOME = Join-Path $deveco 'sdk'
$env:NODE_HOME = Join-Path $deveco 'tools\node'
$nodeExe = Join-Path $env:NODE_HOME 'node.exe'
$hvigorJs = Join-Path $deveco 'tools\hvigor\bin\hvigorw.js'

Write-Host 'DEVECO_SDK_HOME =' $env:DEVECO_SDK_HOME
Write-Host '工程目录        =' $appDir
Write-Host ''

# 用 Start-Process 而不是 & 调用：PowerShell 5.1 会把原生命令的 stderr 当错误记录，
# 开了 ErrorActionPreference=Stop 就会误判成失败。
# 路径里有空格，参数自己加引号，Start-Process 不会替你加。
$quotedHvigor = '"' + $hvigorJs + '"'
$proc = Start-Process -FilePath $nodeExe `
    -ArgumentList @($quotedHvigor, 'assembleHap', '--no-daemon') `
    -WorkingDirectory $appDir `
    -NoNewWindow -Wait -PassThru

if ($proc.ExitCode -ne 0) {
    Write-Host ''
    Write-Host '编译失败。看上面的 ERROR 行；路径含中文是最常见的那个。'
    exit $proc.ExitCode
}

Write-Host ''
Write-Host '编译通过。产物：'
Get-ChildItem (Join-Path $appDir 'entry\build') -Recurse -Filter '*.hap' -ErrorAction SilentlyContinue |
    ForEach-Object { Write-Host ('  ' + $_.FullName + '  (' + [math]::Round($_.Length / 1KB) + ' KB)') }
