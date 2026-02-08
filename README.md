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