package org.backendbrilliance.streammetricsloadgenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.backendbrilliance.streammetrics.model.MetricType;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
@SpringBootApplication
public class StreammetricsLoadGeneratorApplication implements CommandLineRunner {

    private final KafkaTemplate<String, MetricEvent> kafkaTemplate;
    // Metrics tracking
    private final AtomicLong sentCount = new AtomicLong(0);
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);

    public static void main(String[] args) {
        SpringApplication.run(StreammetricsLoadGeneratorApplication.class, args);
    }

    @Override
    public void run(String @NonNull ... args) throws Exception {

        int targetEventsPerSecond = 10000;
        int durationSeconds = 60;
        int numOfThreads = 20;

        log.info("Starting load test:");
        log.info("Target: {} events/sec", targetEventsPerSecond);
        log.info("Duration: {} seconds", durationSeconds);
        log.info("Threads: {}", numOfThreads);
        log.info("Total events: {}", targetEventsPerSecond * durationSeconds);

        long startTime = System.currentTimeMillis();

        ScheduledExecutorService reporter;
        try (ExecutorService executor = Executors.newFixedThreadPool(numOfThreads)) {

            int eventsPerThreadPerSecond = targetEventsPerSecond / numOfThreads;

            reporter = Executors.newScheduledThreadPool(1);
            reporter.scheduleAtFixedRate(this::reportMetrics, 1, 1, TimeUnit.SECONDS);

            // Submit tasks
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < numOfThreads; i++) {
                futures.add(executor.submit(
                        new ProducerWorker(eventsPerThreadPerSecond, durationSeconds, i)
                ));
            }

            // Wait for all threads to complete
            for (Future<?> future : futures) {
                future.get();
            }

            reporter.shutdown();
            executor.shutdown();
        }

        long endTime = System.currentTimeMillis();
        long totalDuration = endTime - startTime;

        // Final report
        log.info("========================================");
        log.info("Load test completed!");
        log.info("Total sent: {}", sentCount.get());
        log.info("Success: {}", successCount.get());
        log.info("Failures: {}", failureCount.get());
        log.info("Duration: {} ms", totalDuration);
        log.info("Actual throughput: {} events/sec", (sentCount.get() * 1000) / totalDuration);
        log.info("========================================");

    }

    private void reportMetrics() {
        long sent = sentCount.get();
        long success = successCount.get();
        long failures = failureCount.get();

        log.info("Progress: sent={}, success={}, failures={}, pending={}",
                sent, success, failures, (sent - success - failures));
    }

    private class ProducerWorker implements Runnable {

        private final int eventsPerSecond;
        private final int durationSeconds;
        private final int workerId;
        private final Random random = new Random();

        private final String[] services = {"payment-service", "user-service", "order-service"};
        private final String[] endpoints = {"/api/payments", "/api/users", "/api/orders"};

        public ProducerWorker(int eventsPerSecond, int durationSeconds, int workerId) {
            this.eventsPerSecond = eventsPerSecond;
            this.durationSeconds = durationSeconds;
            this.workerId = workerId;
        }

        @Override
        public void run() {
            long startTime = System.nanoTime();
            long endTime = startTime + TimeUnit.SECONDS.toNanos(durationSeconds);

            // Calculate delay between events (in nanoseconds)
            long delayNanos = TimeUnit.SECONDS.toNanos(1) / eventsPerSecond;

            long nextSendTime = startTime;
            int eventCount = 0;

            while (System.nanoTime() < endTime) {

                // Send event
                try {
                    MetricEvent event = createEvent();
                    sentCount.incrementAndGet();
                    kafkaTemplate.send("metrics-events", event.getServiceId(), event)
                            .whenComplete((result, ex) -> {
                                if (ex == null) {
                                    successCount.incrementAndGet();
                                } else {
                                    failureCount.incrementAndGet();
                                    log.error("Send failed: {}", ex.getMessage());
                                }
                            });
                    eventCount++;

                } catch (Exception e) {
                    log.error("Error creating event: {}", e.getMessage());
                    failureCount.incrementAndGet();
                }

                // Rate limiting: sleep until next send time
                nextSendTime += delayNanos;
                long sleepNanos = nextSendTime - System.nanoTime();

                if (sleepNanos > 0) {
                    try {
                        TimeUnit.NANOSECONDS.sleep(sleepNanos);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            log.info("Worker {} finished: sent {} events", workerId, eventCount);
        }

        private MetricEvent createEvent() {
            String service = services[random.nextInt(services.length)];
            String endpoint = endpoints[random.nextInt(endpoints.length)];

            return MetricEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .serviceId(service)
                    .instanceId(service + "-pod-" + random.nextInt(5))
                    .metricType(MetricType.HTTP_REQUEST)
                    .metricName("api.request.duration")
                    .value(50.0 + random.nextDouble() * 500)
                    .unit("milliseconds")
                    .tags(Map.of(
                            "endpoint", endpoint,
                            "method", "POST",
                            "status", "200"
                    ))
                    .metadata(Map.of("traceId", UUID.randomUUID().toString()))
                    .build();
        }
    }

}
