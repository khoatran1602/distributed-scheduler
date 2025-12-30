# Setup Infrastructure Script

Write-Host "Starting Infrastructure Setup..."

# Start Redis
# Check if redis-server is already running
if (Get-Process "redis-server" -ErrorAction SilentlyContinue) {
    Write-Host "Redis is already running."
} else {
    Write-Host "Starting Redis..."
    # Start in new window to keep it alive easily, or use Start-Process with -NoNewWindow
    # Using window so user can see it/close it if needed, or background. 
    # For agentic/headless, -NoNewWindow and -PassThru is better, but Redis blocks.
    # We will use Start-Process to detach.
    Start-Process redis-server -WindowStyle Hidden
    Write-Host "Redis started."
}

# Setup PostgreSQL
$pgData = "C:\Users\Public\pgsql_data"
$pgLog = "$pgData\logfile"

if (!(Test-Path $pgData)) {
    Write-Host "Initializing PostgreSQL data directory at $pgData..."
    # Initialize with default postgres user
    & initdb -D $pgData -U postgres -A trust
} else {
    Write-Host "PostgreSQL data directory exists."
}

# Start PostgreSQL
# Check if postgres is running (check lock file or process)
if (Get-Process "postgres" -ErrorAction SilentlyContinue) {
    Write-Host "PostgreSQL is already running."
} else {
    Write-Host "Starting PostgreSQL..."
    & pg_ctl -D $pgData -l $pgLog start
    # Wait for start
    Start-Sleep -Seconds 5
}

# Create Database and User
Write-Host "Configuring Database..."
# Create user (ignore error if exists)
& psql -U postgres -c "DO \`$do\`$ BEGIN IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'scheduler') THEN CREATE ROLE scheduler LOGIN PASSWORD 'scheduler123'; END IF; END \`$do\`$;"
# Create database (ignore error if exists - psql doesn't have CREATE DATABASE IF NOT EXISTS in all versions easily without script, so we allow failure or check)
# Simplest is just try create and ignore error
& psql -U postgres -c "CREATE DATABASE scheduler OWNER scheduler;" 2>$null

Write-Host "Infrastructure Setup Complete."
