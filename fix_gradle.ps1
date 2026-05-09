$gradleFile = "app/build.gradle"
$content = [System.IO.File]::ReadAllText($gradleFile)
# Remove BOM if present (though ReadAllText handles it, writing it back without it is the key)
# Also fix versioning properly this time
$content = $content -replace 'versionCode\s+\d+', 'versionCode 21'
$content = $content -replace 'versionName\s+"\d+\.\d+\.\d+"', 'versionName "1.0.13"'
# Remove any leading garbage characters (like '?')
$content = $content.TrimStart()
# Use UTF8 without BOM
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($gradleFile, $content, $utf8NoBom)
Write-Host "✅ Fixed build.gradle (removed BOM, updated version to 1.0.13 (21))"
