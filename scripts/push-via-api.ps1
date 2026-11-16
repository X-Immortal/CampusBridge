# 用 GitHub Data API 推送一个本地提交（git push 被网络 reset 时的兜底）。
#
# 前提：本机 gh 已登录（gh auth status 显示 account），且远端 main 正好是本地 HEAD 的父提交。
# 用法（在 CampusBridge 目录）：
#   powershell -ExecutionPolicy Bypass -File .\scripts\push-via-api.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\push-via-api.ps1 -Commit 30772b9
#
# 它做四件事：上传改动文件的 blob → 建 tree（base 是远端当前 tree）→ 建 commit → 更新 refs/heads/main。
# 不碰工作区、不改本地历史；成功后本地需要 git fetch + reset --hard origin/main 对齐。

param(
    [string]$Repo = 'X-Immortal/CampusBridge',
    [string]$Commit = 'HEAD',
    [string]$Branch = 'main'
)

$ErrorActionPreference = 'Stop'

$gh = 'C:\Program Files\GitHub CLI\gh.exe'
if (-not (Test-Path -LiteralPath $gh)) {
    throw '找不到 gh.exe：' + $gh
}

$root = Split-Path -Parent $PSScriptRoot
$tmp = Join-Path $env:TEMP ('campusbridge-push-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

try {
    $sha = (git -C $root rev-parse $Commit).Trim()
    $ref = & $gh api "repos/$Repo/git/ref/heads/$Branch" | ConvertFrom-Json
    $baseCommit = $ref.object.sha
    $baseTree = (& $gh api "repos/$Repo/git/commits/$baseCommit" | ConvertFrom-Json).tree.sha

    $parent = (git -C $root rev-parse "$sha^").Trim()
    if ($parent -ne $baseCommit) {
        throw "远端 $Branch 不是本地 $Commit 的父提交：remote=$baseCommit localParent=$parent。先 fetch/rebase。"
    }

    $files = git -C $root -c core.quotepath=false diff-tree --no-commit-id --name-only -r $sha
    if (-not $files) {
        throw "提交 $sha 没有改动文件"
    }

    $entries = @()
    foreach ($rel in $files) {
        $full = Join-Path $root ($rel -replace '/', '\')
        $b64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($full))
        $bodyFile = Join-Path $tmp ('blob-' + [guid]::NewGuid().ToString('N') + '.json')
        $body = @{ content = $b64; encoding = 'base64' } | ConvertTo-Json -Compress
        [System.IO.File]::WriteAllText($bodyFile, $body, (New-Object System.Text.UTF8Encoding($false)))
        $blob = & $gh api --method POST "repos/$Repo/git/blobs" --input $bodyFile | ConvertFrom-Json
        $entries += @{ path = $rel; mode = '100644'; type = 'blob'; sha = $blob.sha }
        Write-Host ('  blob  ' + $rel)
    }

    $treeFile = Join-Path $tmp 'tree.json'
    (@{ base_tree = $baseTree; tree = $entries } | ConvertTo-Json -Depth 6 -Compress) |
        ForEach-Object { [System.IO.File]::WriteAllText($treeFile, $_, (New-Object System.Text.UTF8Encoding($false))) }
    $tree = & $gh api --method POST "repos/$Repo/git/trees" --input $treeFile | ConvertFrom-Json

    $name = (git -C $root config user.name); if (-not $name) { $name = 'Inxer' }
    $email = (git -C $root config user.email); if (-not $email) { $email = 'Inxer@users.noreply.github.com' }
    $message = ((git -C $root log -1 --format=%B $sha) -join "`n").Trim()
    $now = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')

    $commitFile = Join-Path $tmp 'commit.json'
    (@{
            message   = $message
            tree      = $tree.sha
            parents   = @($baseCommit)
            author    = @{ name = $name; email = $email; date = $now }
            committer = @{ name = $name; email = $email; date = $now }
        } | ConvertTo-Json -Depth 6 -Compress) | ForEach-Object { [System.IO.File]::WriteAllText($commitFile, $_, (New-Object System.Text.UTF8Encoding($false))) }
    $newCommit = & $gh api --method POST "repos/$Repo/git/commits" --input $commitFile | ConvertFrom-Json

    $refFile = Join-Path $tmp 'ref.json'
    (@{ sha = $newCommit.sha; force = $false } | ConvertTo-Json -Compress) |
        ForEach-Object { [System.IO.File]::WriteAllText($refFile, $_, (New-Object System.Text.UTF8Encoding($false))) }
    $updated = & $gh api --method PATCH "repos/$Repo/git/refs/heads/$Branch" --input $refFile | ConvertFrom-Json

    Write-Host ''
    Write-Host ('推送上去了：' + $updated.object.sha)
    Write-Host '本地对齐：git fetch origin; git reset --hard origin/main'
} finally {
    Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
