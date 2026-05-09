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
    $patch = [int]$versionNameMatch.Groups[3].Value + 1
    $newVersionName = "$major.$minor.$patch"
    $content = $content -replace "versionName\s+`"$major.$minor.$patch`"", "versionName `"$newVersionName`""
}
$content | Set-Content $gradleFile -NoNewline -Encoding UTF8
Write-Host "✅ 版本号已更新为: $newVersionName ($newCode)"

# 2. 提交并推送
git add .
git commit -m "chore: bump version to $newVersionName and auto commit"
git push
```

## 步骤 2：监听 GitHub Actions 打包状态
使用项目根目录下的 .github_token 来调用 GitHub API，轮询检查最近一次运行的工作流状态。如果失败，将自动抓取最后 50 行日志以供分析。
// turbo
```powershell
$token = (Get-Content .github_token).Trim()
$repo = "slowpack/listener"
$url = "https://api.github.com/repos/$repo/actions/runs?per_page=1"

Write-Host "正在获取最近一次的 GitHub Actions 运行记录..."

while ($true) {
    $response = curl.exe -s -H "Accept: application/vnd.github.v3+json" -H "Authorization: Bearer $token" $url | ConvertFrom-Json
    
    if ($null -eq $response -or $null -eq $response.workflow_runs -or $response.workflow_runs.Count -eq 0) {
        Write-Host "❌ 未找到任何工作流运行记录，请检查仓库名称或 Token 权限。"
        exit 1
    }
    
    $run = $response.workflow_runs[0]
    $runId = $run.id
    
    if ($run.status -eq "completed") {
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
