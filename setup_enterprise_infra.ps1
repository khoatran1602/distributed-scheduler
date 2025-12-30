# Enterprise Infrastructure Setup Script

Write-Host "Starting Enterprise Infrastructure Setup..."

# 1. Start Redis
if (Get-Process "redis-server" -ErrorAction SilentlyContinue) {
    Write-Host "Redis is already running."
} else {
    Write-Host "Starting Redis..."
    Start-Process -FilePath "C:\Users\Hello\scoop\apps\redis\current\redis-server.exe" -WindowStyle Hidden
    Write-Host "Redis started."
}

# 2. Start PostgreSQL
$pgData = "C:\Users\Public\pgsql_data"
$pgLog = "$pgData\logfile"
if (Get-Process "postgres" -ErrorAction SilentlyContinue) {
    Write-Host "PostgreSQL is already running."
} else {
    Write-Host "Starting PostgreSQL..."
    & "C:\Users\Hello\scoop\apps\postgresql\current\bin\pg_ctl.exe" -D $pgData -l $pgLog start
    Start-Sleep -Seconds 5
}

# 3. Start Zookeeper (Required for Kafka)
# Kafka requires Zookeeper (unless using KRaft mode, but standard scoop install uses Zookeeper by default config)
# We use a COPY of Kafka at C:\Users\Public\k to avoid "Input line too long" errors with Scoop paths
$kafkaPath = "C:\Users\Public\k"
$zookeeperConfig = "$kafkaPath\config\zookeeper.properties"
$kafkaConfig = "$kafkaPath\config\server.properties"

# Check if Zookeeper is running (java process listening on 2181)
# Simplified check: just check if we started it or ports (Netstat)
# For now, we'll try to start it in a new window to keep it alive
Write-Host "Starting Zookeeper..."
Start-Process -FilePath "cmd.exe" -ArgumentList "/c $kafkaPath\bin\windows\zookeeper-server-start.bat $zookeeperConfig" -WindowStyle Minimized

Start-Sleep -Seconds 10

# 4. Start Kafka Broker
Write-Host "Starting Kafka Broker..."
Start-Process -FilePath "cmd.exe" -ArgumentList "/c $kafkaPath\bin\windows\kafka-server-start.bat $kafkaConfig" -WindowStyle Minimized

Write-Host "Enterprise Infrastructure (Redis, Postgres, Zookeeper, Kafka) Started."
Write-Host "Note: Schema Registry is NOT installed (requires manual setup or Docker). We will fail gracefully if missing."
