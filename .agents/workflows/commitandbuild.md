---
description: commit@build
---

# 提交并监听打包状态流程 (Commit and Build)

这个工作流用于将本地代码提交、推送到 GitHub，并自动监听 GitHub Actions 的打包状态，直到打包完成并输出成功或失败的结果。

## 步骤 1：自动增加版本号并提交代码

// turbo
```powershell
# 1. 自动增加版本号
$gradleFile = "app/build.gradle"
$content = Get-Content $gradleFile -Raw
$versionCodeMatch = [regex]::Match($content, 'versionCode (\d+)')
if ($versionCodeMatch.Success) {
    $oldCode = $versionCodeMatch.Groups[1].Value
    $newCode = [int]$oldCode + 1
    $content = $content -replace "versionCode $oldCode", "versionCode $newCode"
}
$versionNameMatch = [regex]::Match($content, 'versionName "(\d+)\.(\d+)\.(\d+)"')
if ($versionNameMatch.Success) {
    $major = $versionNameMatch.Groups[1].Value
    $minor = $versionNameMatch.Groups[2].Value
    $patch = [int]$versionNameMatch.Groups[3].Value + 1
    $newVersionName = "$major.$minor.$patch"
    $content = $content -replace "versionName `"$major.$minor.$patch`"", "versionName `"$newVersionName`""
}
$content | Set-Content $gradleFile -NoNewline
Write-Host "✅ 版本号已更新为: $newVersionName ($newCode)"

# 2. 提交并推送
git add .
git commit -m "chore: bump version to $newVersionName and auto commit"
git push
```

## 步骤 2：监听 GitHub Actions 打包状态

使用项目根目录下的 `.github_token` 来调用 GitHub API，轮询检查最近一次运行的工作流状态，直到它变为 `completed`。

// turbo
```powershell
$token = Get-Content .github_token
$repo = "slowpack/listener"
$url = "https://api.github.com/repos/$repo/actions/runs?per_page=1"

Write-Host "正在获取最近一次的 GitHub Actions 运行记录..."

while ($true) {
    # 调用 API 并解析 JSON
    $response = curl.exe -s -H "Accept: application/vnd.github.v3+json" -H "Authorization: Bearer $token" $url | ConvertFrom-Json
    
    # 由于可能返回空数组，需做好保护
    if ($null -eq $response -or $null -eq $response.workflow_runs -or $response.workflow_runs.Count -eq 0) {
        Write-Host "未找到任何工作流运行记录，请检查仓库名称或 Token 权限。"
        exit 1
    }
    
    $run = $response.workflow_runs[0]
    
    if ($run.status -eq "completed") {
        if ($run.conclusion -eq "success") {
            Write-Host "🎉 打包成功完成！"
            Write-Host "可以前往此处下载产物或查看详情: $($run.html_url)"
            break
        } else {
            Write-Host "❌ 打包失败！结论为: $($run.conclusion)"
            Write-Host "查看详细报错: $($run.html_url)"
            exit 1
        }
    }
    
    Write-Host "当前状态: $($run.status)... 等待 10 秒后重新查询。"
    Start-Sleep -Seconds 10
}
```
