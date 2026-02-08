#!/bin/bash

# Create metrics-events topic
docker exec kafka1 kafka-topics --create \
  --bootstrap-server kafka1:9092 \
  --topic metrics-events \
  --partitions 6 \
  --replication-factor 2 \
  --config retention.ms=604800000 \
  --config min.insync.replicas=2

# Create aggregated topics
docker exec kafka1 kafka-topics --create \
  --bootstrap-server kafka1:9092 \
  --topic metrics-aggregated-1m \
  --partitions 3 \
  --replication-factor 2 \
  --config retention.ms=604800000

docker exec kafka1 kafka-topics --create \
  --bootstrap-server kafka1:9092 \
  --topic metrics-aggregated-5m \
  --partitions 3 \
  --replication-factor 2 \
  --config retention.ms=2592000000

# Create dead letter queue
docker exec kafka1 kafka-topics --create \
  --bootstrap-server kafka1:9092 \
  --topic metrics-dead-letter \
  --partitions 3 \
  --replication-factor 2 \
  --config retention.ms=1209600000

# List all topics
docker exec kafka1 kafka-topics --list \
  --bootstrap-server kafka1:9092

# Describe metrics-events topic
docker exec kafka1 kafka-topics --describe \
  --bootstrap-server kafka1:9092 \
  --topic metrics-events