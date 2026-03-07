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

## Monitoring

StreamMetrics includes production-grade monitoring with Prometheus and Grafana.

### Setup Monitoring
```bash
# Install Prometheus + Grafana
helm install prometheus prometheus-community/kube-prometheus-stack \\
  --namespace monitoring \\
  --create-namespace

# Deploy ServiceMonitors
kubectl apply -f k8s/monitoring/servicemonitors/

# Access Grafana
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
# Login: admin / admin123
```

### Dashboards

Import `k8s/monitoring/dashboards/streammetrics-dashboard.json` in Grafana.

**Metrics included:**
- JVM heap, CPU, garbage collection
- HTTP request rate, latency (p95, p99)
- Kafka producer throughput
- Redis operations
- Pod health status

See [k8s/monitoring/README.md](k8s/monitoring/README.md) for details.

