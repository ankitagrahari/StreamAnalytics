# StreamMetrics Monitoring

## Components

- **Prometheus**: Metrics collection
- **Grafana**: Visualization dashboards
- **ServiceMonitors**: Auto-discovery of app metrics
- **Alerts**: Consumer lag, pod health, memory alerts

## Installation
```bash
# Install Prometheus Stack
helm install prometheus prometheus-community/kube-prometheus-stack \\
  --namespace monitoring \\
  --create-namespace \\
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \\
  --set grafana.adminPassword=admin123

# Deploy ServiceMonitors
kubectl apply -f servicemonitors/

# Deploy Redis Exporter
kubectl apply -f redis-exporter.yaml

# Deploy Alerts
kubectl apply -f alerts.yaml
```

## Access Grafana
```bash
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
open http://localhost:3000
# Login: admin / admin123
```

## Import Dashboard

1. In Grafana: Dashboards → Import
2. Upload `dashboards/streammetrics-dashboard.json`
3. Select Prometheus datasource
4. Import

## Key Metrics

- **JVM Heap Usage**: `jvm_memory_used_bytes{area="heap"}`
- **CPU Usage**: `rate(process_cpu_seconds_total[1m])`
- **HTTP Requests/sec**: `rate(http_server_requests_seconds_count[1m])`
- **Kafka Messages Sent**: `rate(spring_kafka_template_seconds_count[1m])`
- **Redis Keys**: `redis_db_keys`