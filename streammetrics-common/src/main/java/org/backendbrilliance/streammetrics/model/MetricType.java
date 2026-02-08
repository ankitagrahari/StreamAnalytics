package org.backendbrilliance.streammetrics.model;

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
