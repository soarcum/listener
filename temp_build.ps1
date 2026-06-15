# 1. Bump version
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
# Save as UTF-8 without BOM
$Utf8NoBomEncoding = New-Object System.Text.UTF8Encoding $False
[System.IO.File]::WriteAllText((Resolve-Path $gradleFile), $content, $Utf8NoBomEncoding)
Write-Host "Version updated to: $newVersionName ($newCode)"

# 2. Commit and Push
git add .
git commit -m "chore: bump version to $newVersionName and auto commit"
$currentSha = (git rev-parse HEAD).Trim()
$currentSha | Set-Content .last_commit_sha -NoNewline
git push

# 3. Poll GitHub Actions
$token = (Get-Content .github_token).Trim()
$repo = "slowpack/listener"
$targetSha = (Get-Content .last_commit_sha).Trim()
$url = "https://api.github.com/repos/$repo/actions/runs?per_page=5"

$headers = @{
    "Accept" = "application/vnd.github.v3+json"
    "Authorization" = "Bearer $token"
    "User-Agent" = "PowerShell"
}

Write-Host "Waiting for GitHub Actions run record for SHA: $($targetSha.Substring(0,7))..."

while ($true) {
    try {
        $response = Invoke-RestMethod -Uri $url -Headers $headers -Method Get
        
        # Find matching run
        $run = $response.workflow_runs | Where-Object { $_.head_sha -eq $targetSha } | Select-Object -First 1
        
        if ($null -eq $run) {
            Write-Host "Waiting for workflow to start..."
            Start-Sleep -Seconds 5
            continue
        }
        
        $runId = $run.id
        
        if ($run.status -eq "completed") {
            # Clean up temp files
            if (Test-Path .last_commit_sha) { Remove-Item .last_commit_sha }
            if (Test-Path temp_build.ps1) { Remove-Item temp_build.ps1 }
            
            if ($run.conclusion -eq "success") {
                Write-Host "Build completed successfully!"
                Write-Host "Download URL: $($run.html_url)"
                break
            } else {
                Write-Host "Build failed! Conclusion: $($run.conclusion)"
                Write-Host "Fetching error logs for the failed job..."
                
                # Get Job ID
                $jobsUrl = "https://api.github.com/repos/$repo/actions/runs/$runId/jobs"
                $jobsResponse = Invoke-RestMethod -Uri $jobsUrl -Headers $headers -Method Get
                $failedJob = $jobsResponse.jobs | Where-Object { $_.conclusion -eq "failure" } | Select-Object -First 1
                
                if ($null -ne $failedJob) {
                    Write-Host "Found failed job: $($failedJob.name) (ID: $($failedJob.id))`n"
                    $logUrl = "https://api.github.com/repos/$repo/actions/jobs/$($failedJob.id)/logs"
                    
                    # Fetch logs
                    try {
                        $webResponse = Invoke-WebRequest -Uri $logUrl -Headers $headers -MaximumRedirection 0 -ErrorAction SilentlyContinue
                        $redirectUrl = $webResponse.Headers.Location
                        if ($null -ne $redirectUrl) {
                            $logs = Invoke-RestMethod -Uri $redirectUrl -Method Get
                        } else {
                            $logs = Invoke-RestMethod -Uri $logUrl -Headers $headers -Method Get
                        }
                    } catch {
                        # Fallback to curl
                        $logs = curl.exe -L -s -H "Authorization: Bearer $token" $logUrl
                    }

                    if ($logs) {
                        $logLines = $logs -split "`n"
                        Write-Host "--- Last 50 lines of log ---"
                        $logLines | Select-Object -Last 50
                        Write-Host "----------------------------"
                    } else {
                        Write-Host "Cannot retrieve log content."
                    }
                }
                
                Write-Host "Check details here: $($run.html_url)"
                break
            }
        }
        
        Write-Host "Current status: $($run.status)... Waiting 10s."
        Start-Sleep -Seconds 10
    } catch {
        Write-Host "Error occurred: $_. Retrying in 5s..."
        Start-Sleep -Seconds 5
    }
}
