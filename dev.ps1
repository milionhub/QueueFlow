$ErrorActionPreference = "Stop"

# Always work from the QueueFlow repository root
$Root = $PSScriptRoot
Set-Location $Root

Write-Host ""
Write-Host "QueueFlow Development" -ForegroundColor Cyan
Write-Host "=====================" -ForegroundColor Cyan
Write-Host ""

# 1. Start PostgreSQL
Write-Host "[1/4] Starting PostgreSQL..." -ForegroundColor Yellow
docker compose up -d

# 2. Load .env into this process
Write-Host "[2/4] Loading environment variables..." -ForegroundColor Yellow

if (-not (Test-Path ".env")) {
    Write-Host "ERROR: .env file not found." -ForegroundColor Red
    exit 1
}

Get-Content ".env" | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable(
            $matches[1].Trim(),
            $matches[2].Trim(),
            "Process"
        )
    }
}

# 3. Start Spring Boot in a new PowerShell window
Write-Host "[3/4] Starting backend..." -ForegroundColor Yellow

$backendCommand = @"
Set-Location '$Root\backend'
.\mvnw.cmd spring-boot:run
"@

Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendCommand

# 4. Start Vite in another PowerShell window
Write-Host "[4/4] Starting frontend..." -ForegroundColor Yellow

$frontendCommand = @"
Set-Location '$Root\frontend'
npm run dev
"@

Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontendCommand

Write-Host ""
Write-Host "QueueFlow is starting!" -ForegroundColor Green
Write-Host ""
Write-Host "Frontend: http://localhost:5173"
Write-Host "Backend:  http://localhost:8080"
Write-Host ""
Write-Host "Two terminal windows were opened for backend and frontend."
Write-Host "PostgreSQL is running through Docker."
Write-Host ""