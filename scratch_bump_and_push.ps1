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
$Utf8NoBomEncoding = New-Object System.Text.UTF8Encoding $False
$absoluteGradlePath = Resolve-Path $gradleFile
[System.IO.File]::WriteAllText($absoluteGradlePath, $content, $Utf8NoBomEncoding)
Write-Host "Version bumped to: $newVersionName ($newCode)"

git add .
git commit -m "chore: bump version to $newVersionName and auto commit"
$currentSha = (git rev-parse HEAD).Trim()
$currentSha | Set-Content .last_commit_sha -NoNewline
git push
