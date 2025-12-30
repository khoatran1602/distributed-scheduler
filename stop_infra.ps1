# Stop Infrastructure Script

Write-Host "Stopping Infrastructure..."

# Stop Redis
# Try graceful shutdown via cli first, else kill process
if (Get-Command "redis-cli" -ErrorAction SilentlyContinue) {
    Write-Host "Stopping Redis via CLI..."
    try {
        redis-cli shutdown
    } catch {
        Write-Host "Redis CLI shutdown failed or Redis not running."
    }
}

# Ensure process is gone
$redisProcess = Get-Process "redis-server" -ErrorAction SilentlyContinue
if ($redisProcess) {
    Write-Host "Killing Redis process..."
    Stop-Process -Name "redis-server" -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "Redis is stopped."
}

# Stop PostgreSQL
$pgData = "C:\Users\Public\pgsql_data"
if (Get-Command "pg_ctl" -ErrorAction SilentlyContinue) {
    Write-Host "Stopping PostgreSQL..."
    & pg_ctl -D $pgData stop
}

# Ensure process is gone
$postgresProcess = Get-Process "postgres" -ErrorAction SilentlyContinue
if ($postgresProcess) {
    Write-Host "Killing remaining PostgreSQL processes..."
    Stop-Process -Name "postgres" -Force -ErrorAction SilentlyContinue
} else {
    Write-Host "PostgreSQL is stopped."
}

Write-Host "Infrastructure Stopped."
