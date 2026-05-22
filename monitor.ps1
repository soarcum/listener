$sha = (git rev-parse HEAD).Trim()
$token = Get-Content -Path ".github_token"
$headers = @{
    "Authorization" = "token $token"
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "Powershell-Monitor"
}
$url = "https://api.github.com/repos/slowpack/listener/actions/runs?head_sha=$sha"

Write-Host "Monitoring Actions workflow run for dynamically fetched SHA $sha..."
$maxAttempts = 80
$attempt = 0
$done = $false

while (-not $done -and $attempt -lt $maxAttempts) {
    $attempt++
    try {
        $response = Invoke-RestMethod -Uri $url -Headers $headers -Method Get
        if ($response.total_count -gt 0) {
            $run = $response.workflow_runs[0]
            $status = $run.status
            $conclusion = $run.conclusion
            Write-Host "Attempt ${attempt} - Status = $status, Conclusion = $conclusion"
            if ($status -eq "completed") {
                $done = $true
                if ($conclusion -eq "success") {
                    Write-Host "SUCCESS: Workflow finished successfully!"
                    exit 0
                } else {
                    Write-Host "FAILED: Workflow completed with conclusion: $conclusion"
                    exit 1
                }
            }
        } else {
            Write-Host "Attempt ${attempt} - Waiting for action to start..."
        }
    } catch {
        Write-Host "Error fetching status: $_"
    }
    Start-Sleep -Seconds 15
}

Write-Host "TIMEOUT: Action monitoring timed out after 20 minutes."
exit 2
