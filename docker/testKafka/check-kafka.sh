#!/bin/bash

echo "=== Kafka Cluster Status ==="
echo ""

for broker in kafka1 kafka2 kafka3; do
  echo "Checking $broker..."
  
  # Check if container is running
  if docker ps | grep -q $broker; then
    echo "✅ Container running"
    
    # Check if Kafka is started
    if docker logs $broker 2>&1 | grep -q "Kafka Server started"; then
      echo "✅ Kafka started"
    else
      echo "❌ Kafka not started yet"
    fi
    
    # Check listeners
    docker exec $broker /usr/bin/kafka-broker-api-versions \
      --bootstrap-server localhost:9092 2>&1 | head -1
  else
    echo "❌ Container not running"
  fi
  
  echo ""
done

echo "=== Testing Connection from Host ==="
timeout 2 bash -c "</dev/tcp/localhost/9092" && echo "✅ Port 9092 accessible" || echo "❌ Port 9092 not accessible"
timeout 2 bash -c "</dev/tcp/localhost/9094" && echo "✅ Port 9094 accessible" || echo "❌ Port 9094 not accessible"
timeout 2 bash -c "</dev/tcp/localhost/9096" && echo "✅ Port 9096 accessible" || echo "❌ Port 9096 not accessible"
