$token = (Get-Content .github_token).Trim()
$repo = "slowpack/listener"
$url = "https://api.github.com/repos/$repo/actions/runs?per_page=1"

Write-Host "Fetching latest GitHub Actions run..."

while ($true) {
    $json = curl.exe -s -H "Accept: application/vnd.github.v3+json" -H "Authorization: Bearer $token" $url
    $response = $json | ConvertFrom-Json
    
    if ($null -eq $response -or $null -eq $response.workflow_runs -or $response.workflow_runs.Count -eq 0) {
        Write-Host "Error: No workflow runs found."
        exit 1
    }
    
    $run = $response.workflow_runs[0]
    $runId = $run.id
    $status = $run.status
    $conclusion = $run.conclusion
    $html_url = $run.html_url
    
    if ($status -eq "completed") {
        if ($conclusion -eq "success") {
            Write-Host "Success! Build completed."
            Write-Host "URL: $html_url"
            break
        } else {
            Write-Host "Failure! Conclusion: $conclusion"
            
            $jobsUrl = "https://api.github.com/repos/$repo/actions/runs/$runId/jobs"
            $jobsJson = curl.exe -s -H "Accept: application/vnd.github.v3+json" -H "Authorization: Bearer $token" $jobsUrl
            $jobsResponse = $jobsJson | ConvertFrom-Json
            $failedJob = $jobsResponse.jobs | Where-Object { $_.conclusion -eq "failure" } | Select-Object -First 1
            
            if ($null -ne $failedJob) {
                $jobId = $failedJob.id
                $jobName = $failedJob.name
                Write-Host "Failed job: $jobName (ID: $jobId)"
                
                $logUrl = "https://api.github.com/repos/$repo/actions/jobs/$jobId/logs"
                $logs = curl.exe -L -s -H "Authorization: Bearer $token" $logUrl
                if ($logs) {
                    $logLines = $logs -split "`n"
                    Write-Host "--- Last 50 lines of log ---"
                    $logLines | Select-Object -Last 50
                    Write-Host "----------------------------"
                }
            }
            Write-Host "Details: $html_url"
            exit 1
        }
    }
    
    Write-Host "Current status: $status... Waiting 10s."
    Start-Sleep -Seconds 10
}
