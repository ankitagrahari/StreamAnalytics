# StreamMetrics - Week 1: Detailed Implementation Plan

## **WEEK 1 GOAL**
Build the core Kafka infrastructure with reliable producer and consumer that handles 10K events/sec with zero data loss.

---

## **DAY 1: Project Setup + Kafka Cluster**

### **Morning Session (3-4 hours)**

#### **Task 1.1: Create Project Structure** (30 min)

```bash
# Create root directory
mkdir streammetrics
cd streammetrics

# Create multi-module Maven project structure
mkdir -p streammetrics-producer
mkdir -p streammetrics-consumer
mkdir -p streammetrics-common
mkdir -p streammetrics-load-generator
mkdir -p docker

# Initialize Git
git init
echo "target/" > .gitignore
echo ".idea/" >> .gitignore
echo "*.iml" >> .gitignore
echo ".DS_Store" >> .gitignore
```

Create **`pom.xml`** (parent):
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.backendbrilliance</groupId>
    <artifactId>streammetrics</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>streammetrics-common</module>
        <module>streammetrics-producer</module>
        <module>streammetrics-consumer</module>
        <module>streammetrics-load-generator</module>
    </modules>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.2</version>
    </parent>

    <properties>
        <java.version>21</java.version>
        <kafka.version>3.6.1</kafka.version>
        <lombok.version>1.18.30</lombok.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.apache.kafka</groupId>
                <artifactId>kafka-clients</artifactId>
                <version>${kafka.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

#### **Task 1.2: Set up Docker Compose for Kafka Cluster** (1 hour)

Create **`docker/docker-compose.yml`**:

```yaml
version: '3.8'

services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    hostname: zookeeper
    container_name: streammetrics-zookeeper
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper-data:/var/lib/zookeeper/data
      - zookeeper-logs:/var/lib/zookeeper/log
    networks:
      - streammetrics-network

  kafka-1:
    image: confluentinc/cp-kafka:7.5.0
    hostname: kafka-1
    container_name: streammetrics-kafka-1
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
      - "19092:19092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 2
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 2
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'false'
      KAFKA_JMX_PORT: 9101
      KAFKA_JMX_HOSTNAME: localhost
    volumes:
      - kafka-1-data:/var/lib/kafka/data
    networks:
      - streammetrics-network

  kafka-2:
    image: confluentinc/cp-kafka:7.5.0
    hostname: kafka-2
    container_name: streammetrics-kafka-2
    depends_on:
      - zookeeper
    ports:
      - "9093:9093"
      - "19093:19093"
    environment:
      KAFKA_BROKER_ID: 2
      KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-2:29093,PLAINTEXT_HOST://localhost:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 2
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 2
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'false'
      KAFKA_JMX_PORT: 9102
      KAFKA_JMX_HOSTNAME: localhost
    volumes:
      - kafka-2-data:/var/lib/kafka/data
    networks:
      - streammetrics-network

  kafka-3:
    image: confluentinc/cp-kafka:7.5.0
    hostname: kafka-3
    container_name: streammetrics-kafka-3
    depends_on:
      - zookeeper
    ports:
      - "9094:9094"
      - "19094:19094"
    environment:
      KAFKA_BROKER_ID: 3
      KAFKA_ZOOKEEPER_CONNECT: 'zookeeper:2181'
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-3:29094,PLAINTEXT_HOST://localhost:9094
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 2
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 2
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'false'
      KAFKA_JMX_PORT: 9103
      KAFKA_JMX_HOSTNAME: localhost
    volumes:
      - kafka-3-data:/var/lib/kafka/data
    networks:
      - streammetrics-network

  redis:
    image: redis:7.2-alpine
    container_name: streammetrics-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - streammetrics-network
    command: redis-server --appendonly yes

  postgres:
    image: postgres:15-alpine
    container_name: streammetrics-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: streammetrics
      POSTGRES_PASSWORD: streammetrics
      POSTGRES_DB: streammetrics
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql
    networks:
      - streammetrics-network

volumes:
  zookeeper-data:
  zookeeper-logs:
  kafka-1-data:
  kafka-2-data:
  kafka-3-data:
  redis-data:
  postgres-data:

networks:
  streammetrics-network:
    driver: bridge
```

Create **`docker/init-db.sql`**:

```sql
-- Create schema
CREATE SCHEMA IF NOT EXISTS metrics;

-- Create metrics table
CREATE TABLE metrics.metric_aggregates (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    service_id VARCHAR(100) NOT NULL,
    metric_name VARCHAR(200) NOT NULL,
    window_size VARCHAR(10) NOT NULL,
    count BIGINT,
    sum DOUBLE PRECISION,
    avg DOUBLE PRECISION,
    min DOUBLE PRECISION,
    max DOUBLE PRECISION,
    p50 DOUBLE PRECISION,
    p95 DOUBLE PRECISION,
    p99 DOUBLE PRECISION,
    tags JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes
CREATE INDEX idx_metrics_lookup 
ON metrics.metric_aggregates(service_id, metric_name, timestamp DESC);

CREATE INDEX idx_metrics_tags 
ON metrics.metric_aggregates USING GIN(tags);

CREATE INDEX idx_metrics_timestamp 
ON metrics.metric_aggregates(timestamp DESC);
```

#### **Task 1.3: Start Kafka Cluster and Verify** (30 min)

```bash
# Start cluster
cd docker
docker-compose up -d

# Wait 30 seconds for startup
sleep 30

# Verify all containers running
docker-compose ps

# Expected output: All services "Up"
```

Create **`docker/create-topics.sh`**:

```bash
#!/bin/bash

# Create metrics-events topic
docker exec streammetrics-kafka-1 kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic metrics-events \
  --partitions 6 \
  --replication-factor 2 \
  --config retention.ms=604800000 \
  --config min.insync.replicas=2

# Create aggregated topics
docker exec streammetrics-kafka-1 kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic metrics-aggregated-1m \
  --partitions 3 \
  --replication-factor 2 \
  --config retention.ms=604800000

docker exec streammetrics-kafka-1 kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic metrics-aggregated-5m \
  --partitions 3 \
  --replication-factor 2 \
  --config retention.ms=2592000000

# Create dead letter queue
docker exec streammetrics-kafka-1 kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic metrics-dead-letter \
  --partitions 3 \
  --replication-factor 2 \
  --config retention.ms=1209600000

# List all topics
docker exec streammetrics-kafka-1 kafka-topics --list \
  --bootstrap-server localhost:9092

# Describe metrics-events topic
docker exec streammetrics-kafka-1 kafka-topics --describe \
  --bootstrap-server localhost:9092 \
  --topic metrics-events
```

```bash
chmod +x create-topics.sh
./create-topics.sh
```

### **Afternoon Session (3-4 hours)**

#### **Task 1.4: Create Common Module** (1 hour)

Create **`streammetrics-common/pom.xml`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>com.backendbrilliance</groupId>
        <artifactId>streammetrics</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>streammetrics-common</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

Create **`streammetrics-common/src/main/java/com/backendbrilliance/streammetrics/model/MetricEvent.java`**:

```java
package com.backendbrilliance.streammetrics.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single metric event in the system.
 * This is the core data model sent through Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricEvent {
    
    @NotBlank(message = "Event ID cannot be blank")
    @JsonProperty("event_id")
    private String eventId;
    
    @NotNull(message = "Timestamp cannot be null")
    @JsonProperty("timestamp")
    private Instant timestamp;
    
    @NotBlank(message = "Service ID cannot be blank")
    @JsonProperty("service_id")
    private String serviceId;
    
    @NotBlank(message = "Instance ID cannot be blank")
    @JsonProperty("instance_id")
    private String instanceId;
    
    @NotNull(message = "Metric type cannot be null")
    @JsonProperty("metric_type")
    private MetricType metricType;
    
    @NotBlank(message = "Metric name cannot be blank")
    @JsonProperty("metric_name")
    private String metricName;
    
    @NotNull(message = "Value cannot be null")
    @Positive(message = "Value must be positive")
    @JsonProperty("value")
    private Double value;
    
    @JsonProperty("unit")
    private String unit;
    
    @JsonProperty("tags")
    private Map<String, String> tags;
    
    @JsonProperty("metadata")
    private Map<String, String> metadata;
    
    /**
     * Creates a new MetricEvent with auto-generated ID and current timestamp
     */
    public static MetricEvent create(String serviceId, String instanceId, 
                                     MetricType metricType, String metricName, 
                                     Double value, String unit) {
        return MetricEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .serviceId(serviceId)
                .instanceId(instanceId)
                .metricType(metricType)
                .metricName(metricName)
                .value(value)
                .unit(unit)
                .build();
    }
}
```

Create **`streammetrics-common/src/main/java/com/backendbrilliance/streammetrics/model/MetricType.java`**:

```java
package com.backendbrilliance.streammetrics.model;

public enum MetricType {
    HTTP_REQUEST,
    DATABASE_QUERY,
    CACHE_HIT,
    CACHE_MISS,
    EXTERNAL_API_CALL,
    MESSAGE_QUEUE_PUBLISH,
    MESSAGE_QUEUE_CONSUME,
    CUSTOM_COUNTER,
    CUSTOM_GAUGE,
    CUSTOM_HISTOGRAM
}
```

#### **Task 1.5: Create Serializers/Deserializers** (1 hour)

Create **`streammetrics-common/src/main/java/com/backendbrilliance/streammetrics/serde/MetricEventSerializer.java`**:

```java
package com.backendbrilliance.streammetrics.serde;

import com.backendbrilliance.streammetrics.model.MetricEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetricEventSerializer implements Serializer<MetricEvent> {
    
    private final ObjectMapper objectMapper;
    
    public MetricEventSerializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public byte[] serialize(String topic, MetricEvent data) {
        if (data == null) {
            return null;
        }
        
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (Exception e) {
            log.error("Error serializing MetricEvent: {}", data, e);
            throw new SerializationException("Error serializing MetricEvent", e);
        }
    }
}
```

Create **`streammetrics-common/src/main/java/com/backendbrilliance/streammetrics/serde/MetricEventDeserializer.java`**:

```java
package com.backendbrilliance.streammetrics.serde;

import com.backendbrilliance.streammetrics.model.MetricEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetricEventDeserializer implements Deserializer<MetricEvent> {
    
    private final ObjectMapper objectMapper;
    
    public MetricEventDeserializer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Override
    public MetricEvent deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        
        try {
            return objectMapper.readValue(data, MetricEvent.class);
        } catch (Exception e) {
            log.error("Error deserializing MetricEvent from bytes", e);
            throw new SerializationException("Error deserializing MetricEvent", e);
        }
    }
}
```

#### **Task 1.6: Write Unit Tests** (1 hour)

Create **`streammetrics-common/src/test/java/com/backendbrilliance/streammetrics/serde/MetricEventSerdeTest.java`**:

```java
package com.backendbrilliance.streammetrics.serde;

import com.backendbrilliance.streammetrics.model.MetricEvent;
import com.backendbrilliance.streammetrics.model.MetricType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricEventSerdeTest {
    
    private final MetricEventSerializer serializer = new MetricEventSerializer();
    private final MetricEventDeserializer deserializer = new MetricEventDeserializer();
    
    @Test
    void shouldSerializeAndDeserializeMetricEvent() {
        // Given
        MetricEvent original = MetricEvent.builder()
                .eventId("test-123")
                .timestamp(Instant.now())
                .serviceId("payment-service")
                .instanceId("payment-pod-1")
                .metricType(MetricType.HTTP_REQUEST)
                .metricName("api.request.duration")
                .value(245.5)
                .unit("milliseconds")
                .tags(Map.of("endpoint", "/api/payments", "method", "POST"))
                .metadata(Map.of("traceId", "abc123"))
                .build();
        
        // When
        byte[] serialized = serializer.serialize("test-topic", original);
        MetricEvent deserialized = deserializer.deserialize("test-topic", serialized);
        
        // Then
        assertNotNull(serialized);
        assertNotNull(deserialized);
        assertEquals(original.getEventId(), deserialized.getEventId());
        assertEquals(original.getServiceId(), deserialized.getServiceId());
        assertEquals(original.getValue(), deserialized.getValue());
        assertEquals(original.getTags(), deserialized.getTags());
    }
    
    @Test
    void shouldHandleNullGracefully() {
        // When/Then
        assertNull(serializer.serialize("test-topic", null));
        assertNull(deserializer.deserialize("test-topic", null));
    }
}
```

### **Evening: Documentation** (1 hour)

Create **`README.md`**:

```markdown
# StreamMetrics

Real-time metrics processing platform built with Kafka and Spring Boot.

## Day 1 Progress

✅ Project structure created
✅ Kafka cluster (3 brokers) running
✅ Topics created with proper replication
✅ Common module with domain models
✅ Custom serializers/deserializers
✅ Unit tests passing

## Quick Start

```bash
# Start infrastructure
cd docker
docker-compose up -d

# Create topics
./create-topics.sh

# Build project
cd ..
mvn clean install
```

## Architecture

See `docs/architecture.md` for details.

## Next Steps (Day 2)

- Implement Kafka producer
- Add retry logic and error handling
- Create idempotent producer configuration
```

---

## **DAY 2: Reliable Kafka Producer**

### **Morning Session**

#### **Task 2.1: Create Producer Module** (30 min)

Create **`streammetrics-producer/pom.xml`**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>com.backendbrilliance</groupId>
        <artifactId>streammetrics</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>streammetrics-producer</artifactId>
    
    <dependencies>
        <dependency>
            <groupId>com.backendbrilliance</groupId>
            <artifactId>streammetrics-common</artifactId>
            <version>${project.version}</version>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka</artifactId>
        </dependency>
        
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
            <version>2.1.0</version>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>kafka</artifactId>
            <version>1.19.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

#### **Task 2.2: Configure Producer** (1 hour)

Create **`streammetrics-producer/src/main/resources/application.yml`**:

```yaml
spring:
  application:
    name: streammetrics-producer
    
  kafka:
    bootstrap-servers: localhost:9092,localhost:9093,localhost:9094
    producer:
      # Serializers
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: com.backendbrilliance.streammetrics.serde.MetricEventSerializer
      
      # Reliability settings
      acks: all
      retries: 2147483647  # Infinite retries
      max-in-flight-requests-per-connection: 5
      enable-idempotence: true
      
      # Performance tuning
      compression-type: snappy
      batch-size: 32768  # 32KB
      linger-ms: 10
      buffer-memory: 67108864  # 64MB
      
      # Timeout settings
      request-timeout-ms: 30000
      delivery-timeout-ms: 120000
      
      # Additional properties
      properties:
        max.block.ms: 60000
        
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
        
logging:
  level:
    com.backendbrilliance: DEBUG
    org.apache.kafka: INFO
```

Create **`streammetrics-producer/src/main/java/com/backendbrilliance/streammetrics/config/KafkaProducerConfig.java`**:

```java
package com.backendbrilliance.streammetrics.config;

import com.backendbrilliance.streammetrics.model.MetricEvent;
import com.backendbrilliance.streammetrics.serde.MetricEventSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public ProducerFactory<String, MetricEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        
        // Connection
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serializers
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, MetricEventSerializer.class);
        
        // Reliability (Exactly-Once Semantics)
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Performance
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768);
        config.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        config.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864);
        
        // Timeouts
        config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 60000);
        
        return new DefaultKafkaProducerFactory<>(config);
    }
    
    @Bean
    public KafkaTemplate<String, MetricEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```