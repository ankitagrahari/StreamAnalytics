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