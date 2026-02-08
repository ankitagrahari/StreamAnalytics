# StreamMetrics - Complete Architecture

## **System Overview**

StreamMetrics is a real-time analytics platform that processes application metrics from multiple Spring Boot microservices using Kafka, providing sub-second visibility into system health and performance.

## **High-Level Architecture**

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Producer Applications                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ Service A    │  │ Service B    │  │ Service C    │              │
│  │ (Spring Boot)│  │ (Spring Boot)│  │ (Spring Boot)│              │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘              │
│         │                  │                  │                       │
│         │  Metrics Events  │                  │                       │
│         └──────────────────┴──────────────────┘                       │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
                          ▼
         ┌────────────────────────────────────┐
         │        Kafka Cluster               │
         │  ┌──────────┐  ┌──────────┐       │
         │  │ Broker 1 │  │ Broker 2 │       │
         │  └──────────┘  └──────────┘       │
         │  Topic: metrics-events             │
         │  Partitions: 6, Replication: 2     │
         └────────────┬───────────────────────┘
                      │
                      ▼
         ┌────────────────────────────────────┐
         │   StreamMetrics Consumer Layer     │
         │                                    │
         │  ┌─────────────────────────────┐  │
         │  │  Kafka Streams Processor    │  │
         │  │  - Aggregations             │  │
         │  │  - Windowing                │  │
         │  │  - State Stores             │  │
         │  └──────────┬──────────────────┘  │
         │             │                      │
         │  ┌──────────▼──────────────────┐  │
         │  │  Consumer Group              │  │
         │  │  - Consumers: 3              │  │
         │  │  - Partition assignment      │  │
         │  └──────────┬──────────────────┘  │
         └─────────────┼──────────────────────┘
                       │
           ┌───────────┴───────────┐
           │                       │
           ▼                       ▼
    ┌──────────────┐      ┌──────────────┐
    │    Redis     │      │  PostgreSQL  │
    │  (Cache)     │      │  (Persist)   │
    │              │      │              │
    │ - Hot data   │      │ - Historical │
    │ - Counters   │      │ - Analytics  │
    │ - TTL: 1hr   │      │ - Retention  │
    └──────┬───────┘      └──────┬───────┘
           │                     │
           └──────────┬──────────┘
                      │
                      ▼
           ┌──────────────────────┐
           │   Query API Layer    │
           │   (Spring Boot)      │
           │                      │
           │  - REST endpoints    │
           │  - WebSocket stream  │
           │  - GraphQL (future)  │
           └──────────┬───────────┘
                      │
                      ▼
           ┌──────────────────────┐
           │  Dashboard UI        │
           │  (Vaadin/React)      │
           └──────────────────────┘
```

## **Data Flow**

### **1. Event Production**
```
Application → Micrometer → Custom Exporter → Kafka Producer → Kafka Topic
```

**Event Schema:**
```json
{
  "eventId": "uuid-v4",
  "timestamp": 1707267600000,
  "serviceId": "payment-service",
  "instanceId": "payment-service-pod-3",
  "metricType": "HTTP_REQUEST",
  "metricName": "api.request.duration",
  "value": 245.5,
  "unit": "milliseconds",
  "tags": {
    "endpoint": "/api/v1/payments",
    "method": "POST",
    "statusCode": "200",
    "region": "us-east-1"
  },
  "metadata": {
    "traceId": "abc123",
    "spanId": "def456"
  }
}
```

### **2. Stream Processing**
```
Kafka → Kafka Streams → Aggregation → State Store → Output Topics
```

**Processing Pipeline:**
1. **Deserialization**: JSON → POJO
2. **Validation**: Schema validation, range checks
3. **Enrichment**: Add derived fields, lookup metadata
4. **Windowing**: 
   - Tumbling: 1 min, 5 min, 15 min
   - Sliding: 1 min window, 10 sec advance
5. **Aggregation**:
   - Count, Sum, Avg, Min, Max
   - Percentiles (p50, p95, p99)
   - Rate calculations
6. **Output**: Materialized views, output topics

### **3. Storage Strategy**

**Redis (Hot Path - Last 1 hour):**
```
Key Pattern: metrics:{serviceId}:{metricName}:{window}
Value: Aggregated statistics
TTL: 3600 seconds
Example: metrics:payment-service:api.request.duration:1m → {count: 1234, avg: 245, p95: 450}
```

**PostgreSQL (Cold Path - Historical):**
```sql
CREATE TABLE metric_aggregates (
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

CREATE INDEX idx_metrics_lookup ON metric_aggregates(service_id, metric_name, timestamp DESC);
CREATE INDEX idx_metrics_tags ON metric_aggregates USING GIN(tags);
```

## **Technology Stack**

### **Core Infrastructure**
- **Kafka**: 3.6.0 (Multi-broker cluster)
- **Zookeeper**: 3.8.0 (Kafka dependency)
- **Redis**: 7.2 (In-memory cache)
- **PostgreSQL**: 15 (Time-series extension)

### **Application Stack**
- **Java**: 21 (LTS)
- **Spring Boot**: 3.2.x
- **Spring Kafka**: 3.1.x
- **Kafka Streams**: 3.6.x
- **Spring Data JPA**: Latest
- **Spring Data Redis**: Latest

### **Supporting Tools**
- **Docker & Docker Compose**: Local dev environment
- **Testcontainers**: Integration testing
- **JUnit 5**: Unit testing
- **Micrometer**: Metrics collection
- **Lombok**: Reduce boilerplate

## **Kafka Topic Design**

### **Topics**

**1. metrics-events (Input)**
- **Partitions**: 6
- **Replication Factor**: 2
- **Retention**: 7 days
- **Partition Key**: `serviceId`
- **Purpose**: Raw metric events from all services

**2. metrics-aggregated-1m (Output)**
- **Partitions**: 3
- **Replication Factor**: 2
- **Retention**: 7 days
- **Partition Key**: `serviceId`
- **Purpose**: 1-minute window aggregations

**3. metrics-aggregated-5m (Output)**
- **Partitions**: 3
- **Replication Factor**: 2
- **Retention**: 30 days
- **Partition Key**: `serviceId`
- **Purpose**: 5-minute window aggregations

**4. metrics-dead-letter (DLQ)**
- **Partitions**: 3
- **Replication Factor**: 2
- **Retention**: 14 days
- **Purpose**: Failed/invalid events for debugging

### **Partitioning Strategy**

**Why partition by serviceId?**
1. **Locality**: All metrics from same service go to same partition
2. **Ordering**: Guarantees order within a service
3. **Scalability**: Different services can be processed in parallel
4. **Consumer affinity**: Each consumer handles specific services

## **Consumer Group Design**

```
Consumer Group: streammetrics-processors
  Consumer 1: Processes partitions [0, 1]
  Consumer 2: Processes partitions [2, 3]
  Consumer 3: Processes partitions [4, 5]
```

**Configuration:**
```yaml
Consumer Group Settings:
  group.id: streammetrics-processors
  auto.offset.reset: earliest
  enable.auto.commit: false  # Manual commit for exactly-once
  max.poll.records: 500
  max.poll.interval.ms: 300000
  session.timeout.ms: 10000
  heartbeat.interval.ms: 3000
  isolation.level: read_committed  # For transactional reads
```

## **Producer Configuration**

**Reliability Settings:**
```yaml
Producer Settings:
  acks: all                          # Wait for all ISR
  retries: 2147483647               # Max int (infinite)
  max.in.flight.requests.per.connection: 5
  enable.idempotence: true          # Exactly-once delivery
  compression.type: snappy          # Fast compression
  batch.size: 32768                 # 32KB batches
  linger.ms: 10                     # Small delay for batching
  buffer.memory: 67108864           # 64MB buffer
```

## **Error Handling Strategy**

### **1. Producer Errors**
```
Network Error → Retry (with exponential backoff)
Serialization Error → Log + Dead Letter Queue
Broker Unavailable → Circuit Breaker → Fallback
```

### **2. Consumer Errors**

**Recoverable Errors:**
- Transient network issues → Retry with backoff
- Deserialization errors → Skip + DLQ
- Processing errors → Retry (max 3 times) → DLQ

**Non-recoverable Errors:**
- Invalid schema → DLQ immediately
- Business logic violations → DLQ immediately

### **3. Dead Letter Queue Pattern**

```java
try {
    processMetricEvent(event);
    commitOffset();
} catch (RecoverableException e) {
    retry(event, attempt++);
} catch (NonRecoverableException e) {
    sendToDeadLetterQueue(event, e);
    commitOffset();
}
```

## **Exactly-Once Semantics**

**How we achieve it:**

1. **Idempotent Producer**
   - `enable.idempotence=true`
   - Kafka deduplicates on broker side

2. **Transactional Writes**
   ```java
   kafkaTemplate.executeInTransaction(ops -> {
       ops.send("metrics-aggregated-1m", result);
       return true;
   });
   ```

3. **Offset Management**
   - Manual offset commits
   - Commit only after successful processing
   - Store offsets in Kafka transactions

## **Performance Targets**

### **Week 1 Goals**
- **Throughput**: 10K events/sec
- **Latency (p99)**: < 100ms end-to-end
- **Data Loss**: 0 events lost
- **Availability**: 99.9% (downtime < 44 sec/hour)

### **Week 4 Goals (Full System)**
- **Throughput**: 100K events/sec
- **Latency (p99)**: < 50ms end-to-end
- **Data Loss**: 0 events lost
- **Availability**: 99.99% (downtime < 4.3 sec/hour)

## **Observability**

**Metrics to Track:**
```
Producer:
  - kafka.producer.record-send-rate
  - kafka.producer.record-error-rate
  - kafka.producer.request-latency-avg

Consumer:
  - kafka.consumer.records-consumed-rate
  - kafka.consumer.fetch-latency-avg
  - kafka.consumer.commit-latency-avg

Application:
  - processing.duration (histogram)
  - processing.errors (counter)
  - dlq.messages.sent (counter)
  - cache.hit.ratio (gauge)
```

## **Testing Strategy**

### **Unit Tests**
- Serialization/Deserialization
- Business logic validation
- Error handling paths

### **Integration Tests**
- Kafka producer/consumer flow
- Database operations
- Redis caching

### **Performance Tests**
- Load testing with varied event rates
- Stress testing at 2x target throughput
- Chaos testing (kill brokers, consumers)

## **Deployment Architecture**

```
Docker Compose (Week 1):
  - Kafka Cluster (3 brokers)
  - Zookeeper (1 instance)
  - Redis (1 instance)
  - PostgreSQL (1 instance)
  - StreamMetrics App (3 instances)

Kubernetes (Week 4):
  - Kafka via Strimzi Operator
  - StatefulSets for stateful services
  - Deployments for StreamMetrics
  - HPA for auto-scaling
```

## **CAP Theorem Tradeoffs**

**Our Choices:**

1. **Kafka** (CP - Consistency + Partition Tolerance)
   - Strong consistency with replication
   - Trades availability during leader election
   - Perfect for financial/critical metrics

2. **Redis** (AP - Availability + Partition Tolerance)
   - Eventually consistent
   - Always available for reads
   - Acceptable for recent metrics cache

3. **Overall System** (Hybrid)
   - Critical path: CP (via Kafka)
   - Query path: AP (via Redis)
   - Best of both worlds

## **Data Retention Policy**

```
Redis: 1 hour (hot data)
  ↓ (async write)
PostgreSQL: 
  - Raw aggregates: 90 days
  - Rolled up (hourly): 1 year
  - Rolled up (daily): 3 years
  ↓ (archive)
S3/Cold Storage: Indefinite (compressed)
```

## **Security Considerations**

**Week 1 (Basic):**
- SASL/PLAIN for Kafka authentication
- SSL/TLS for data in transit
- Network isolation via Docker networks

**Week 4 (Production):**
- SASL/SCRAM or mTLS
- Encryption at rest
- ACLs for topic access
- Secrets management via Vault

## **Next Steps After Week 1**

1. **Week 2**: Add Kafka Streams processing
2. **Week 3**: Implement observability stack
3. **Week 4**: Production hardening + K8s
4. **Weeks 5-8**: Add Spring AI + RAG

---

## **Success Criteria for Week 1**

✅ Kafka cluster running with 3 brokers
✅ Producer sending 10K events/sec reliably
✅ Consumer processing with 0 data loss
✅ Dead letter queue handling errors
✅ All integration tests passing
✅ Clear documentation of architecture
✅ Blog post published with learnings
