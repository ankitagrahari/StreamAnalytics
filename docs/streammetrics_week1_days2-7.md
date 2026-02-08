# StreamMetrics - Week 1: Days 2-7 Complete Implementation

## **DAY 2 (CONTINUED): Producer Implementation**

### **Task 2.3: Create Producer Service** (2 hours)

Create **`streammetrics-producer/src/main/java/com/backendbrilliance/streammetrics/service/MetricProducerService.java`**:

```java
package com.backendbrilliance.streammetrics.service;

import com.backendbrilliance.streammetrics.model.MetricEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricProducerService {
    
    private final KafkaTemplate<String, MetricEvent> kafkaTemplate;
    
    @Value("${app.kafka.topic.metrics-events}")
    private String topic;
    
    /**
     * Sends a metric event to Kafka with retry and circuit breaker patterns
     * 
     * @param event The metric event to send
     * @return CompletableFuture with send result
     */
    @Retry(name = "kafkaProducer", fallbackMethod = "sendToDeadLetterQueue")
    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "sendToDeadLetterQueue")
    public CompletableFuture<SendResult<String, MetricEvent>> sendMetric(MetricEvent event) {
        
        // Use serviceId as partition key for locality
        String partitionKey = event.getServiceId();
        
        log.debug("Sending metric event: eventId={}, serviceId={}, metricName={}", 
                 event.getEventId(), event.getServiceId(), event.getMetricName());
        
        CompletableFuture<SendResult<String, MetricEvent>> future = 
            kafkaTemplate.send(topic, partitionKey, event);
        
        // Add callbacks
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Metric sent successfully: eventId={}, partition={}, offset={}", 
                         event.getEventId(), 
                         result.getRecordMetadata().partition(),
                         result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send metric: eventId={}", event.getEventId(), ex);
            }
        });
        
        return future;
    }
    
    /**
     * Fallback method when main send fails after all retries
     */
    private CompletableFuture<SendResult<String, MetricEvent>> sendToDeadLetterQueue(
            MetricEvent event, Exception ex) {
        
        log.error("All retry attempts exhausted for event: {}. Sending to DLQ.", 
                 event.getEventId(), ex);
        
        // Send to dead letter queue
        return kafkaTemplate.send("metrics-dead-letter", event.getEventId(), event);
    }
    
    /**
     * Synchronous send for critical metrics (blocks until ack)
     */
    public SendResult<String, MetricEvent> sendMetricSync(MetricEvent event) 
            throws Exception {
        
        log.warn("Synchronous send used for event: {} - This blocks the thread!", 
                event.getEventId());
        
        return kafkaTemplate.send(topic, event.getServiceId(), event).get();
    }
}
```

Create **`application.yml`** additions:

```yaml
app:
  kafka:
    topic:
      metrics-events: metrics-events
      metrics-dead-letter: metrics-dead-letter

resilience4j:
  retry:
    instances:
      kafkaProducer:
        max-attempts: 3
        wait-duration: 1s
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - org.apache.kafka.common.errors.NetworkException
          - org.apache.kafka.common.errors.TimeoutException
        ignore-exceptions:
          - org.apache.kafka.common.errors.SerializationException
          
  circuitbreaker:
    instances:
      kafkaProducer:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        sliding-window-size: 10
        minimum-number-of-calls: 5
        permitted-number-of-calls-in-half-open-state: 3
```

### **Task 2.4: Create Test Producer Client** (1 hour)

Create **`streammetrics-producer/src/main/java/com/backendbrilliance/streammetrics/client/TestMetricProducer.java`**:

```java
package com.backendbrilliance.streammetrics.client;

import com.backendbrilliance.streammetrics.model.MetricEvent;
import com.backendbrilliance.streammetrics.model.MetricType;
import com.backendbrilliance.streammetrics.service.MetricProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = "com.backendbrilliance.streammetrics")
@RequiredArgsConstructor
public class TestMetricProducer {
    
    private final MetricProducerService producerService;
    private final Random random = new Random();
    
    public static void main(String[] args) {
        SpringApplication.run(TestMetricProducer.class, args);
    }
    
    @Bean
    public CommandLineRunner sendTestMetrics() {
        return args -> {
            log.info("Starting to send test metrics...");
            
            String[] services = {"payment-service", "user-service", "order-service"};
            String[] endpoints = {"/api/v1/payments", "/api/v1/users", "/api/v1/orders"};
            String[] methods = {"GET", "POST", "PUT", "DELETE"};
            
            for (int i = 0; i < 100; i++) {
                String serviceId = services[random.nextInt(services.length)];
                String endpoint = endpoints[random.nextInt(endpoints.length)];
                String method = methods[random.nextInt(methods.length)];
                
                MetricEvent event = MetricEvent.create(
                    serviceId,
                    serviceId + "-pod-" + random.nextInt(3),
                    MetricType.HTTP_REQUEST,
                    "api.request.duration",
                    50.0 + random.nextDouble() * 500,
                    "milliseconds"
                );
                
                event.setTags(Map.of(
                    "endpoint", endpoint,
                    "method", method,
                    "statusCode", String.valueOf(200 + random.nextInt(100))
                ));
                
                event.setMetadata(Map.of(
                    "traceId", UUID.randomUUID().toString(),
                    "spanId", UUID.randomUUID().toString()
                ));
                
                producerService.sendMetric(event);
                
                // Small delay to avoid overwhelming
                TimeUnit.MILLISECONDS.sleep(10);
            }
            
            log.info("Finished sending 100 test metrics");
        };
    }
}
```

### **Task 2.5: Integration Tests** (1 hour)

Create **`streammetrics-producer/src/test/java/com/backendbrilliance/streammetrics/service/MetricProducerServiceIntegrationTest.java`**:

```java
package com.backendbrilliance.streammetrics.service;

import com.backendbrilliance.streammetrics.model.MetricEvent;
import com.backendbrilliance.streammetrics.model.MetricType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(
    topics = {"metrics-events"},
    partitions = 3,
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9999",
        "port=9999"
    }
)
class MetricProducerServiceIntegrationTest {
    
    @Autowired
    private MetricProducerService producerService;
    
    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;
    
    @Test
    void shouldSendMetricEventSuccessfully() throws Exception {
        // Given
        BlockingQueue<ConsumerRecord<String, MetricEvent>> records = new LinkedBlockingQueue<>();
        
        ContainerProperties containerProps = new ContainerProperties("metrics-events");
        containerProps.setMessageListener((MessageListener<String, MetricEvent>) records::add);
        
        KafkaMessageListenerContainer<String, MetricEvent> container = 
            createContainer(containerProps);
        container.start();
        
        ContainerTestUtils.waitForAssignment(container, 
            embeddedKafkaBroker.getPartitionsPerTopic());
        
        MetricEvent event = MetricEvent.create(
            "test-service",
            "test-pod-1",
            MetricType.HTTP_REQUEST,
            "api.request.duration",
            123.45,
            "milliseconds"
        );
        
        // When
        CompletableFuture<SendResult<String, MetricEvent>> future = 
            producerService.sendMetric(event);
        
        SendResult<String, MetricEvent> result = future.get(10, TimeUnit.SECONDS);
        
        // Then
        assertNotNull(result);
        assertEquals("metrics-events", result.getRecordMetadata().topic());
        
        // Verify consumer received it
        ConsumerRecord<String, MetricEvent> received = 
            records.poll(10, TimeUnit.SECONDS);
        
        assertNotNull(received);
        assertEquals(event.getEventId(), received.value().getEventId());
        assertEquals(event.getServiceId(), received.value().getServiceId());
        
        container.stop();
    }
    
    private KafkaMessageListenerContainer<String, MetricEvent> createContainer(
            ContainerProperties containerProps) {
        
        Map<String, Object> consumerProps = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, 
                embeddedKafkaBroker.getBrokersAsString(),
            ConsumerConfig.GROUP_ID_CONFIG, "test-group",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, 
                StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
                MetricEventDeserializer.class
        );
        
        DefaultKafkaConsumerFactory<String, MetricEvent> cf = 
            new DefaultKafkaConsumerFactory<>(consumerProps);
        
        return new KafkaMessageListenerContainer<>(cf, containerProps);
    }
}
```

---

## **DAY 3: Consumer Implementation**

### **Morning: Create Consumer Module**

Create **`streammetrics-consumer/pom.xml`** (similar to producer, add consumer dependencies)

Create **`streammetrics-consumer/src/main/resources/application.yml`**:

```yaml
spring:
  application:
    name: streammetrics-consumer
    
  kafka:
    bootstrap-servers: localhost:9092,localhost:9093,localhost:9094
    consumer:
      # Consumer group
      group-id: streammetrics-processors
      
      # Deserializers
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: com.backendbrilliance.streammetrics.serde.MetricEventDeserializer
      
      # Offset management
      auto-offset-reset: earliest
      enable-auto-commit: false  # Manual commit for exactly-once
      
      # Performance
      max-poll-records: 500
      max-poll-interval-ms: 300000
      session-timeout-ms: 10000
      heartbeat-interval-ms: 3000
      
      # Reliability
      isolation-level: read_committed
      
      properties:
        partition.assignment.strategy: org.apache.kafka.clients.consumer.CooperativeStickyAssignor

app:
  kafka:
    topic:
      metrics-events: metrics-events
      metrics-aggregated-1m: metrics-aggregated-1m
      metrics-dead-letter: metrics-dead-letter
```

Create **`MetricConsumerService.java`**:

```java
package com.backendbrilliance.streammetrics.consumer;

import com.backendbrilliance.streammetrics.model.MetricEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetricConsumerService {
    
    private final Validator validator;
    private final KafkaTemplate<String, MetricEvent> deadLetterProducer;
    private final MetricProcessor metricProcessor;
    
    @KafkaListener(
        topics = "${app.kafka.topic.metrics-events}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"  // 3 consumer threads
    )
    public void consumeMetric(
            @Payload ConsumerRecord<String, MetricEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        MetricEvent event = record.value();
        
        log.debug("Received metric: eventId={}, partition={}, offset={}", 
                 event.getEventId(), partition, offset);
        
        try {
            // Validate event
            Set<ConstraintViolation<MetricEvent>> violations = validator.validate(event);
            if (!violations.isEmpty()) {
                log.error("Invalid metric event: {}", violations);
                sendToDeadLetterQueue(event, "Validation failed: " + violations);
                acknowledgment.acknowledge();
                return;
            }
            
            // Process metric
            metricProcessor.process(event);
            
            // Manual commit after successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed metric: eventId={}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Error processing metric: eventId={}", event.getEventId(), e);
            sendToDeadLetterQueue(event, e.getMessage());
            acknowledgment.acknowledge();  // Still commit to avoid reprocessing
        }
    }
    
    private void sendToDeadLetterQueue(MetricEvent event, String reason) {
        log.warn("Sending to DLQ: eventId={}, reason={}", event.getEventId(), reason);
        
        // Add error metadata
        event.getMetadata().put("dlq_reason", reason);
        event.getMetadata().put("dlq_timestamp", String.valueOf(System.currentTimeMillis()));
        
        deadLetterProducer.send("metrics-dead-letter", event.getEventId(), event);
    }
}
```

Create **`MetricProcessor.java`**:

```java
package com.backendbrilliance.streammetrics.processor;

import com.backendbrilliance.streammetrics.model.MetricEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricProcessor {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    public void process(MetricEvent event) {
        // For now, just store in Redis
        // Week 2 will add proper aggregation
        
        String key = String.format("metrics:%s:%s:raw",
                                   event.getServiceId(),
                                   event.getMetricName());
        
        redisTemplate.opsForList().rightPush(key, event);
        redisTemplate.expire(key, 1, TimeUnit.HOURS);
        
        // Update counter
        String counterKey = String.format("metrics:%s:%s:count",
                                         event.getServiceId(),
                                         event.getMetricName());
        redisTemplate.opsForValue().increment(counterKey);
        
        log.debug("Stored metric in Redis: key={}", key);
    }
}
```

---

## **DAY 4: Testing & Performance**

### **Task 4.1: Load Generator** (2 hours)

Create **`streammetrics-load-generator`** module:

```java
package com.backendbrilliance.streammetrics.loadgen;

import com.backendbrilliance.streammetrics.model.MetricEvent;
import com.backendbrilliance.streammetrics.model.MetricType;
import com.backendbrilliance.streammetrics.service.MetricProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoadGenerator implements CommandLineRunner {
    
    private final MetricProducerService producerService;
    
    private static final int TARGET_EVENTS_PER_SECOND = 10_000;
    private static final int DURATION_SECONDS = 60;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("Starting load test: {} events/sec for {} seconds",
                TARGET_EVENTS_PER_SECOND, DURATION_SECONDS);
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        Random random = new Random();
        
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (DURATION_SECONDS * 1000L);
        
        AtomicLong sentCount = new AtomicLong(0);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);
        
        // Calculate delay between events
        long delayNanos = TimeUnit.SECONDS.toNanos(1) / TARGET_EVENTS_PER_SECOND;
        
        while (System.currentTimeMillis() < endTime) {
            long batchStart = System.nanoTime();
            
            for (int i = 0; i < 100; i++) {
                MetricEvent event = generateRandomEvent(random);
                
                executor.submit(() -> {
                    try {
                        producerService.sendMetric(event)
                            .thenAccept(result -> successCount.incrementAndGet())
                            .exceptionally(ex -> {
                                failureCount.incrementAndGet();
                                return null;
                            });
                        sentCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    }
                });
            }
            
            // Rate limiting
            long elapsed = System.nanoTime() - batchStart;
            long sleepNanos = (delayNanos * 100) - elapsed;
            if (sleepNanos > 0) {
                Thread.sleep(sleepNanos / 1_000_000, (int)(sleepNanos % 1_000_000));
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        long totalTime = System.currentTimeMillis() - startTime;
        
        log.info("Load test completed!");
        log.info("Total sent: {}", sentCount.get());
        log.info("Successful: {}", successCount.get());
        log.info("Failed: {}", failureCount.get());
        log.info("Duration: {} ms", totalTime);
        log.info("Actual rate: {} events/sec", 
                (sentCount.get() * 1000L) / totalTime);
    }
    
    private MetricEvent generateRandomEvent(Random random) {
        String[] services = {"payment", "user", "order", "inventory", "notification"};
        String serviceId = services[random.nextInt(services.length)] + "-service";
        
        return MetricEvent.create(
            serviceId,
            serviceId + "-pod-" + random.nextInt(5),
            MetricType.HTTP_REQUEST,
            "api.request.duration",
            10.0 + random.nextDouble() * 1000,
            "milliseconds"
        );
    }
}
```

### **Task 4.2: Monitoring Script** (1 hour)

Create **`scripts/monitor-kafka.sh`**:

```bash
#!/bin/bash

echo "=== Kafka Cluster Health ==="
docker exec streammetrics-kafka-1 kafka-broker-api-versions \
  --bootstrap-server localhost:9092 | head -1

echo ""
echo "=== Topic Details ==="
docker exec streammetrics-kafka-1 kafka-topics \
  --describe \
  --bootstrap-server localhost:9092 \
  --topic metrics-events

echo ""
echo "=== Consumer Group Status ==="
docker exec streammetrics-kafka-1 kafka-consumer-groups \
  --describe \
  --bootstrap-server localhost:9092 \
  --group streammetrics-processors

echo ""
echo "=== Message Count ==="
docker exec streammetrics-kafka-1 kafka-run-class kafka.tools.GetOffsetShell \
  --broker-list localhost:9092 \
  --topic metrics-events \
  --time -1 \
  | awk -F':' '{sum += $3} END {print "Total messages: " sum}'
```

---

## **DAY 5: Documentation & Blog Post**

### **Tasks:**
1. Complete README with architecture diagrams
2. Add inline code comments
3. Create troubleshooting guide
4. Write blog post: "Building a Production-Grade Kafka Producer"

**Blog Post Outline:**
```markdown
# Building a Production-Grade Kafka Producer in Spring Boot

## Introduction
- What we're building
- Why reliability matters

## Core Concepts
1. Idempotent Producers
2. Acks Configuration
3. Retry Logic
4. Dead Letter Queues

## Implementation
[Code snippets with explanations]

## Testing at Scale
- Load test results
- Performance benchmarks

## Lessons Learned
- Pitfalls to avoid
- Best practices

## Next Steps
- Week 2: Kafka Streams processing
```

---

## **DAY 6: Integration & Testing**

### **End-to-End Test:**
1. Start entire stack
2. Run load generator
3. Verify 0 data loss
4. Check consumer lag
5. Validate DLQ handling

### **Performance Validation:**
```bash
# Run load test
mvn spring-boot:run -pl streammetrics-load-generator

# Monitor in separate terminal
./scripts/monitor-kafka.sh

# Check Redis
redis-cli
> KEYS metrics:*
> LLEN metrics:payment-service:api.request.duration:raw
```

---

## **DAY 7: Code Review & Prep for Week 2**

### **Checklist:**
✅ All tests passing
✅ 10K events/sec achieved
✅ Zero data loss confirmed
✅ Documentation complete
✅ Blog post published
✅ GitHub repo public

### **Week 2 Prep:**
- Review Kafka Streams documentation
- Read about state stores
- Plan aggregation logic

---

## **DELIVERABLES CHECKLIST**

**Code:**
- [ ] Multi-module Maven project
- [ ] Common module with domain models
- [ ] Producer with idempotent config
- [ ] Consumer with manual offset commits
- [ ] Load generator
- [ ] All unit tests passing
- [ ] Integration tests with Testcontainers

**Infrastructure:**
- [ ] 3-broker Kafka cluster running
- [ ] Topics created with replication
- [ ] Redis connected
- [ ] PostgreSQL connected

**Documentation:**
- [ ] README with quick start
- [ ] Architecture diagram
- [ ] Code comments
- [ ] Troubleshooting guide

**Blog Posts:**
- [ ] Day 1: Kafka cluster setup
- [ ] Day 7: Production Kafka patterns

**Metrics:**
- [ ] Achieving 10K events/sec
- [ ] < 100ms p99 latency
- [ ] 0 events lost
- [ ] DLQ handling working

---

## **TROUBLESHOOTING GUIDE**

**Issue:** Producer not sending
```bash
# Check Kafka brokers
docker ps | grep kafka

# Check topic exists
docker exec streammetrics-kafka-1 kafka-topics --list \
  --bootstrap-server localhost:9092

# Check producer logs
docker logs -f streammetrics-producer
```

**Issue:** Consumer not receiving
```bash
# Check consumer group
docker exec streammetrics-kafka-1 kafka-consumer-groups \
  --describe --bootstrap-server localhost:9092 \
  --group streammetrics-processors

# Reset offsets if needed
docker exec streammetrics-kafka-1 kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group streammetrics-processors \
  --reset-offsets --to-earliest \
  --topic metrics-events --execute
```

**Issue:** Performance not meeting target
```bash
# Check JVM settings
export JAVA_OPTS="-Xms2g -Xmx2g"

# Increase batch size
# In application.yml: batch-size: 65536

# Check network latency
ping kafka-1
```

---

## **NEXT: Week 2 Preview**

We'll add:
- Kafka Streams for aggregation
- Windowing (tumbling, sliding)
- State stores
- Percentile calculations
- Multi-window support

Get ready to process 100K events/sec!
