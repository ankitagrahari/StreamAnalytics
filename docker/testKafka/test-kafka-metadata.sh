# Replace CONTAINER_NAME with your actual kafka container name
KAFKA_CONTAINER="kafka1"  # or kafka-1, or streammetrics-kafka-1

# Test 1: Check broker versions
docker exec $KAFKA_CONTAINER kafka-broker-api-versions \
  --bootstrap-server localhost:9092

# Test 2: List topics
docker exec $KAFKA_CONTAINER kafka-topics \
  --list \
  --bootstrap-server localhost:9092

# Test 3: Describe a topic
docker exec $KAFKA_CONTAINER kafka-topics \
  --describe \
  --topic metrics-events \
  --bootstrap-server localhost:9092