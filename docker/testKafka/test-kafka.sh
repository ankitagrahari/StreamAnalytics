#!/bin/bash

echo "=== Docker Containers ==="
docker ps

echo ""
echo "=== Kafka Container Names ==="
docker ps --format '{{.Names}}' | grep -i kafka

echo ""
echo "=== Testing First Kafka Container ==="
KAFKA_CONTAINER=$(docker ps --format '{{.Names}}' | grep -i kafka | head -1)
docker exec "$KAFKA_CONTAINER" /usr/bin/kafka-broker-api-versions --bootstrap-server localhost:9092 2>&1 | head -5

echo ""
echo "=== Topics ==="
docker exec "$KAFKA_CONTAINER" /usr/bin/kafka-topics --list --bootstrap-server localhost:9092

echo ""
echo "=== Port Bindings ==="
docker ps --format 'table {{.Names}}\t{{.Ports}}' | grep -i kafka

echo ""
echo "Checking if Kafka process is running.."
docker exec "$KAFKA_CONTAINER" ps aux | grep kafka

echo ""
echo "Checking if port 9092 is listening.."
docker exec kafka1 netstat -tuln | grep 9092

echo ""
echo "Test connection and list metadata.."
echo "Download kcat -- brew install kcat"
kcat -b localhost:9092 -L

