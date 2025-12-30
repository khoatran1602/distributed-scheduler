package com.demo.scheduler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HIGH-CONCURRENCY LOAD TESTER
 * ============================
 * 
 * Standalone Java application to stress-test the Distributed Task Scheduler.
 * NO SPRING DEPENDENCIES - Uses raw Java HttpClient for maximum performance.
 * 
 * Configuration:
 * - THREAD_COUNT: Number of concurrent threads (default: 50)
 * - REQUESTS_PER_THREAD: Requests each thread will send (default: 10,000)
 * - Total requests = THREAD_COUNT * REQUESTS_PER_THREAD = 500,000
 * 
 * Usage:
 *   java -cp target/classes com.demo.scheduler.LoadTester
 * 
 * Or with custom settings:
 *   java -cp target/classes com.demo.scheduler.LoadTester <threads> <requests_per_thread>
 */
public class LoadTester {

    // Configuration
    private static final String BASE_URL = "http://localhost:8080/api/tasks";
    private static int THREAD_COUNT = 50;
    private static int REQUESTS_PER_THREAD = 10_000;

    // Metrics
    private static final AtomicLong successCount = new AtomicLong(0);
    private static final AtomicLong failureCount = new AtomicLong(0);
    private static final AtomicLong totalLatency = new AtomicLong(0);

    public static void main(String[] args) {
        // Parse command line arguments
        if (args.length >= 2) {
            THREAD_COUNT = Integer.parseInt(args[0]);
            REQUESTS_PER_THREAD = Integer.parseInt(args[1]);
        }

        int totalRequests = THREAD_COUNT * REQUESTS_PER_THREAD;

        System.out.println("""
            
            ╔═══════════════════════════════════════════════════════════╗
            ║           MILLION TASK LOAD TESTER                       ║
            ╚═══════════════════════════════════════════════════════════╝
            """);
        System.out.println("Configuration:");
        System.out.println("  Target URL:          " + BASE_URL);
        System.out.println("  Thread Count:        " + THREAD_COUNT);
        System.out.println("  Requests/Thread:     " + REQUESTS_PER_THREAD);
        System.out.println("  Total Requests:      " + String.format("%,d", totalRequests));
        System.out.println();

        // Create HTTP client with connection pooling
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        System.out.println("Starting load test...");
        System.out.println("═══════════════════════════════════════════════════════════");

        long startTime = System.currentTimeMillis();

        // Submit workers
        for (int t = 0; t < THREAD_COUNT; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    runWorker(httpClient, threadId);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Progress monitoring thread
        Thread monitor = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    long success = successCount.get();
                    long failure = failureCount.get();
                    long total = success + failure;
                    double progress = (total * 100.0) / totalRequests;
                    long elapsed = System.currentTimeMillis() - startTime;
                    double rps = elapsed > 0 ? (total * 1000.0) / elapsed : 0;
                    
                    System.out.printf("\r  Progress: %6.2f%% | Sent: %,d | Success: %,d | Failed: %,d | RPS: %,.0f",
                        progress, total, success, failure, rps);
                    
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        monitor.setDaemon(true);
        monitor.start();

        // Wait for all workers to complete
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.currentTimeMillis();
        monitor.interrupt();

        // Calculate results
        long totalTime = endTime - startTime;
        long success = successCount.get();
        long failure = failureCount.get();
        long totalSent = success + failure;
        double avgLatency = totalSent > 0 ? (double) totalLatency.get() / totalSent : 0;
        double rps = totalTime > 0 ? (totalSent * 1000.0) / totalTime : 0;

        // Print results
        System.out.println();
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("                    LOAD TEST RESULTS                       ");
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println();
        System.out.printf("  Total Time:          %,d ms (%.2f seconds)%n", totalTime, totalTime / 1000.0);
        System.out.printf("  Total Requests:      %,d%n", totalSent);
        System.out.printf("  Successful:          %,d (%.2f%%)%n", success, (success * 100.0) / totalSent);
        System.out.printf("  Failed:              %,d (%.2f%%)%n", failure, (failure * 100.0) / totalSent);
        System.out.printf("  Avg Latency:         %.2f ms%n", avgLatency);
        System.out.println();
        System.out.println("  ┌────────────────────────────────────────┐");
        System.out.printf("  │  REQUESTS PER SECOND (RPS): %,10.0f │%n", rps);
        System.out.println("  └────────────────────────────────────────┘");
        System.out.println();

        executor.shutdown();
    }

    /**
     * Worker method that sends REQUESTS_PER_THREAD POST requests.
     */
    private static void runWorker(HttpClient client, int threadId) {
        for (int i = 0; i < REQUESTS_PER_THREAD; i++) {
            try {
                long requestStart = System.currentTimeMillis();

                // Create unique payload for each task
                String payload = String.format(
                    "{\"payload\": \"Task from thread-%d, request-%d, timestamp-%d\"}",
                    threadId, i, System.currentTimeMillis()
                );

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                long latency = System.currentTimeMillis() - requestStart;
                totalLatency.addAndGet(latency);

                if (response.statusCode() == 201 || response.statusCode() == 200) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                    if (failureCount.get() <= 10) {
                        System.err.println("\nRequest failed: " + response.statusCode() + " - " + response.body());
                    }
                }

            } catch (Exception e) {
                failureCount.incrementAndGet();
                if (failureCount.get() <= 10) {
                    System.err.println("\nRequest error: " + e.getMessage());
                }
            }
        }
    }
}
