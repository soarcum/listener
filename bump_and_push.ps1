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
