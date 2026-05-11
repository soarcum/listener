---
description: commit@build
---

# 提交并监听打包状态 (Commit and Build)

这个工作流用于将本地代码提交、推送到 GitHub，并自动监听 GitHub Actions 的打包状态。如果打包失败，会自动尝试获取并显示失败日志。

## 步骤 1：自动增加版本号并提交代码
// turbo
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
    $oldPatch = $versionNameMatch.Groups[3].Value
    $patch = [int]$oldPatch + 1
    $newVersionName = "$major.$minor.$patch"
    $oldVersionName = "$major.$minor.$oldPatch"
    $content = $content -replace "versionName\s+`"$oldVersionName`"", "versionName `"$newVersionName`""
}
# 关键修复：保存为不带 BOM 的 UTF-8
$Utf8NoBomEncoding = New-Object System.Text.UTF8Encoding $False
[System.IO.File]::WriteAllText((Resolve-Path $gradleFile), $content, $Utf8NoBomEncoding)
Write-Host "✅ 版本号已更新为: $newVersionName ($newCode)"

# 2. 提交并推送
git add .
git commit -m "chore: bump version to $newVersionName and auto commit"
$currentSha = (git rev-parse HEAD).Trim()
$currentSha | Set-Content .last_commit_sha -NoNewline
git push
```

## 步骤 2：监听 GitHub Actions 打包状态
使用项目根目录下的 .github_token 来调用 GitHub API，轮询检查匹配本次提交 SHA 的工作流状态。如果失败，将自动抓取最后 50 行日志以供分析。
// turbo
```powershell
$token = (Get-Content .github_token).Trim()
$repo = "slowpack/listener"
$targetSha = (Get-Content .last_commit_sha).Trim()
$url = "https://api.github.com/repos/$repo/actions/runs?per_page=5"

Write-Host "正在等待提交 ($($targetSha.Substring(0,7))) 的 GitHub Actions 运行记录..."

while ($true) {
    $response = curl.exe -s -H "Accept: application/vnd.github.v3+json" -H "Authorization: Bearer $token" $url | ConvertFrom-Json
    
    # 查找匹配当前 SHA 的运行记录
    $run = $response.workflow_runs | Where-Object { $_.head_sha -eq $targetSha } | Select-Object -First 1
    
    if ($null -eq $run) {
        Write-Host "等待任务启动..."
        Start-Sleep -Seconds 5
        continue
    }
    
    $runId = $run.id
    
    if ($run.status -eq "completed") {
        # 清理临时文件
        if (Test-Path .last_commit_sha) { Remove-Item .last_commit_sha }
        
        if ($run.conclusion -eq "success") {
            Write-Host "🎉 打包成功完成！"
            Write-Host "🔗 产物下载或详情: $($run.html_url)"
            break
        } else {
            Write-Host "❌ 打包失败！结论为: $($run.conclusion)"
            Write-Host "🔍 正在自动获取失败任务的错误日志..."
            
            # 获取 Job ID
            $jobsUrl = "https://api.github.com/repos/$repo/actions/runs/$runId/jobs"
            $jobsResponse = curl.exe -s -H "Accept: application/vnd.github.v3+json" -H "Authorization: Bearer $token" $jobsUrl | ConvertFrom-Json
            $failedJob = $jobsResponse.jobs | Where-Object { $_.conclusion -eq "failure" } | Select-Object -First 1
            
            if ($null -ne $failedJob) {
                Write-Host "Found failed job: $($failedJob.name) (ID: $($failedJob.id))`n"
                $logUrl = "https://api.github.com/repos/$repo/actions/jobs/$($failedJob.id)/logs"
                
                # 获取日志并显示最后 50 行
                $logs = curl.exe -L -s -H "Authorization: Bearer $token" $logUrl
                if ($logs) {
                    $logLines = $logs -split "`n"
                    Write-Host "--- Last 50 lines of log ---"
                    $logLines | Select-Object -Last 50
                    Write-Host "----------------------------"
                } else {
                    Write-Host "无法获取日志内容。"
                }
            }
            
            Write-Host "`n🔗 查看详细报错详情: $($run.html_url)"
            exit 1
        }
    }
    
    Write-Host "当前状态: $($run.status)... 等待 10 秒后重新查询。"
    Start-Sleep -Seconds 10
}
```
