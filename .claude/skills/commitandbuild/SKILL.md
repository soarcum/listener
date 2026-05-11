---
name: commitandbuild
description: commit@build - 自动增加版本号、提交推送代码、监听 GitHub Actions 打包状态
when_to_use: 当用户说 "提交打包" / "commit and build" / "commit@build" 时触发
---

# 提交并监听打包状态

这个 skill 用于将本地代码提交、推送到 GitHub，并自动监听 GitHub Actions 的打包状态。如果打包失败，会自动获取失败日志。

## 执行步骤

### 步骤 1：自动增加版本号并提交代码

运行以下 PowerShell 脚本完成版本号自增、提交和推送：

```powershell
# 1. 自动增加版本号
$gradleFile = "app/build.gradle"
$content = Get-Content $gradleFile -Raw
$versionCodeMatch = [regex]::Match($content, 'versionCode\s+(\d+)')
if ($versionCodeMatch.Success) {
    $oldCode = $versionCodeMatch.Groups[1].Value
    $newCode = [int]$oldCode + 1
    $content = $content -replace "versionCode\s+$oldCode", "versionCode $newCode"
}
$versionNameMatch = [regex]::Match($content, 'versionName\s+"(\d+)\.(\d+)\.(\d+)"')
if ($versionNameMatch.Success) {
    $major = $versionNameMatch.Groups[1].Value
    $minor = $versionNameMatch.Groups[2].Value
    $patch = [int]$versionNameMatch.Groups[3].Value + 1
    $newVersionName = "$major.$minor.$patch"
    $oldVersionPattern = [regex]::Escape("$major.$minor.$patch")
    $content = $content -replace "versionName\s+`"$oldVersionPattern`"", "versionName `"$newVersionName`""
}
# 保存为不带 BOM 的 UTF-8
$Utf8NoBomEncoding = New-Object System.Text.UTF8Encoding $False
[System.IO.File]::WriteAllText((Resolve-Path $gradleFile), $content, $Utf8NoBomEncoding)
Write-Host "版本号已更新为: $newVersionName ($newCode)"

# 2. 提交并推送
git add .
git commit -m "chore: bump version to $newVersionName and auto commit"
$currentSha = (git rev-parse HEAD).Trim()
$currentSha | Set-Content .last_commit_sha -NoNewline
git push
```

### 步骤 2：监听 GitHub Actions 打包状态

使用 `.github_token` 调用 GitHub API，轮询检查匹配本次提交 SHA 的工作流状态：

```powershell
$token = (Get-Content .github_token).Trim()
$repo = "slowpack/listener"
$targetSha = (Get-Content .last_commit_sha).Trim()
$url = "https://api.github.com/repos/$repo/actions/runs?per_page=5"

Write-Host "正在等待提交 ($($targetSha.Substring(0,7))) 的 GitHub Actions 运行记录..."

while ($true) {
    $response = curl.exe -s -H "Accept: application/vnd.github.v3+json" -H "Authorization: Bearer $token" $url | ConvertFrom-Json
    $run = $response.workflow_runs | Where-Object { $_.head_sha -eq $targetSha } | Select-Object -First 1

    if ($null -eq $run) {
        Write-Host "等待任务启动..."
        Start-Sleep -Seconds 5
        continue
    }

    $runId = $run.id

    if ($run.status -eq "completed") {
        if (Test-Path .last_commit_sha) { Remove-Item .last_commit_sha }

        if ($run.conclusion -eq "success") {
            Write-Host "打包成功完成！"
            Write-Host "产物下载或详情: $($run.html_url)"
            break
        } else {
            Write-Host "打包失败！结论为: $($run.conclusion)"
            Write-Host "正在自动获取失败任务的错误日志..."

            $jobsUrl = "https://api.github.com/repos/$repo/actions/runs/$runId/jobs"
            $jobsResponse = curl.exe -s -H "Accept: application/vnd.github.v3+json" -H "Authorization: Bearer $token" $jobsUrl | ConvertFrom-Json
            $failedJob = $jobsResponse.jobs | Where-Object { $_.conclusion -eq "failure" } | Select-Object -First 1

            if ($null -ne $failedJob) {
                Write-Host "Found failed job: $($failedJob.name) (ID: $($failedJob.id))`n"
                $logUrl = "https://api.github.com/repos/$repo/actions/jobs/$($failedJob.id)/logs"
                $logs = curl.exe -L -s -H "Authorization: Bearer $token" $logUrl
                if ($logs) {
                    $logLines = $logs -split "`n"
                    Write-Host "--- Last 50 lines of log ---"
                    $logLines | Select-Object -Last 50
                    Write-Host "----------------------------"
                }
            }
            Write-Host "查看详细报错详情: $($run.html_url)"
            exit 1
        }
    }

    Write-Host "当前状态: $($run.status)... 等待 10 秒后重新查询。"
    Start-Sleep -Seconds 10
}
```

## 使用方式

当用户说 "提交打包" / "commit and build" / "commit@build" 时触发此 skill。
