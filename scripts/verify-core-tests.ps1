$ErrorActionPreference = 'Stop'
$backend = Join-Path $PSScriptRoot '..\backend'
Push-Location $backend
try {
    Write-Host 'Running focused core business tests...' -ForegroundColor Cyan
    mvn -Dtest=GlobalExceptionHandlerTest,PracticeServiceTest,PaperServiceTest,ExamServiceTest,GradeServiceTest,StatisticsServiceTest,RedisJsonCacheTest test
    if ($LASTEXITCODE -ne 0) { throw "Core business tests failed (exit code $LASTEXITCODE). Check target/surefire-reports." }
    Write-Host 'Core business tests passed.' -ForegroundColor Green
} finally {
    Pop-Location
}
