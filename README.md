
# Walkthrough: Distributed Scheduler Setup

The Distributed Scheduler project is now running as an **Enterprise Grade** system.

## Infrastructure Setup
Since Docker was not available, the following local infrastructure was set up:
- **Redis**: Installed and running (via `scoop install redis`).
- **PostgreSQL**: Installed and running (via `scoop install postgresql`).
- **Kafka & Zookeeper**: Installed and running from `C:\Users\Public\k`.
- **JDK 21**: Installed.

## Starting the Project
We provided a script to handle the complex infrastructure startup:

1.  **Start Infrastructure**:
    ```powershell
    ./setup_enterprise_infra.ps1
    ```
2.  **Run Application**:
    ```powershell
    $env:JAVA_HOME = "C:\Users\Hello\scoop\apps\temurin21-jdk\current"
    & "$env:JAVA_HOME\bin\java.exe" -jar target\distributed-scheduler-1.0.0.jar
    ```

## Usage: How to Input Data

### Option 1: Visual Dashboard (Recommended)
1.  Open [http://localhost:8080/dashboard.html](http://localhost:8080/dashboard.html).
2.  Enter a **Payload** (e.g., "Process Order #1").
3.  Enter **Quantity** (e.g., 50).
4.  Click **🚀 Launch Task**.
5.  Watch the flow diagram animate and the table populate.

### Option 2: Command Line (curl)
You can submit tasks programmatically:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/tasks" -Method Post -Body '{"payload":"Verification Task"}' -ContentType "application/json"
```

## 4. Inspector & Dynamic Switching
The dashboard now includes advanced features for real-time monitoring and configuration.

### Redis Inspector
When running in **Redis Mode**, the dashboard shows:
- **Server Stats**: Uptime, Memory, Client count.
- **Queue Stats**: Real-time queue depth.
- **Message Flow**: Visual log of produced and consumed messages.

### Dynamic Broker Switching
You can switch between Kafka and Redis **without restarting**:
1.  Click the **Broker Badge** (e.g., `REDIS`) in the top-left header.
2.  Confirm the switch.
3.  The system will instantly re-route new tasks to the selected broker.

## 5. Deployment
Build the project:
```bash
mvn clean package -DskipTests
```
Run the jar:
```bash
java -jar target/distributed-scheduler-1.0.0.jar
```

## Verification Results
- **Task Submission**: Verified working via Dashboard and API.
- **Data Flow**: Producer -> Kafka -> Consumer -> PostgreSQL verified.
- **Recent Tasks**: Verified updating correctly on the dashboard.
