$token = (Get-Content .github_token).Trim()
$repo = "slowpack/listener"
$targetSha = (Get-Content .last_commit_sha).Trim()
$url = "https://api.github.com/repos/$repo/actions/runs?per_page=5"

$headers = @{
    "Accept" = "application/vnd.github.v3+json"
    "Authorization" = "Bearer $token"
    "User-Agent" = "PowerShell"
}

Write-Host "Waiting for GitHub Actions run record for SHA ($($targetSha.Substring(0,7)))..."

while ($true) {
    try {
        $response = Invoke-RestMethod -Uri $url -Headers $headers -Method Get
        
        # Find run matching current SHA
        $run = $response.workflow_runs | Where-Object { $_.head_sha -eq $targetSha } | Select-Object -First 1
        
        if ($null -eq $run) {
            Write-Host "Waiting for run to start..."
            Start-Sleep -Seconds 5
            continue
        }
        
        $runId = $run.id
        
        if ($run.status -eq "completed") {
            if (Test-Path .last_commit_sha) { Remove-Item .last_commit_sha }
            
            if ($run.conclusion -eq "success") {
                Write-Host "SUCCESS"
                Write-Host "URL: $($run.html_url)"
                break
            } else {
                Write-Host "FAILED: $($run.conclusion)"
                Write-Host "Fetching failed job error logs..."
                
                $jobsUrl = "https://api.github.com/repos/$repo/actions/runs/$runId/jobs"
                $jobsResponse = Invoke-RestMethod -Uri $jobsUrl -Headers $headers -Method Get
                $failedJob = $jobsResponse.jobs | Where-Object { $_.conclusion -eq "failure" } | Select-Object -First 1
                
                if ($null -ne $failedJob) {
                    Write-Host "Found failed job: $($failedJob.name) (ID: $($failedJob.id))`n"
                    $logUrl = "https://api.github.com/repos/$repo/actions/jobs/$($failedJob.id)/logs"
                    
                    try {
                        $webResponse = Invoke-WebRequest -Uri $logUrl -Headers $headers -MaximumRedirection 0 -ErrorAction SilentlyContinue
                        $redirectUrl = $webResponse.Headers.Location
                        if ($null -ne $redirectUrl) {
                            $logs = Invoke-RestMethod -Uri $redirectUrl -Method Get
                        } else {
                            $logs = Invoke-RestMethod -Uri $logUrl -Headers $headers -Method Get
                        }
                    } catch {
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
                
                Write-Host "`nView details: $($run.html_url)"
                exit 1
            }
        }
        
        Write-Host "Current status: $($run.status)... waiting 10s."
        Start-Sleep -Seconds 10
    } catch {
        Write-Host "Request error. Retrying in 5s..."
        Start-Sleep -Seconds 5
    }
}
