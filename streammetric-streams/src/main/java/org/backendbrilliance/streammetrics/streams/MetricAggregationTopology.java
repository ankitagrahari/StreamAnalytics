package org.backendbrilliance.streammetrics.streams;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.backendbrilliance.streammetrics.model.AggregatedMetric;
import org.backendbrilliance.streammetrics.model.MetricEvent;
import org.backendbrilliance.streammetrics.serde.MetricEventDeserializer;
import org.backendbrilliance.streammetrics.serde.MetricEventSerializer;
import org.backendbrilliance.streammetrics.serde.AggregatedMetricDeserializer;
import org.backendbrilliance.streammetrics.serde.AggregatedMetricSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;

import java.time.Duration;
import java.time.Instant;

@Configuration
@EnableKafkaStreams
public class MetricAggregationTopology {

    @Value("${app.kafka.topics.input}")
    private String inputTopic;

    @Value("${app.kafka.topics.output-1m}")
    private String outputTopic;

    @Value("${app.streams.window.size-minutes}")
    private int windowSizeMinutes;

    @Value("${app.streams.window.grace-period-minutes}")
    private int gracePeriodMinutes;

    @Bean
    public KStream<String, MetricEvent> aggregationStream(StreamsBuilder builder) {
        //1. Read from topic
        KStream<String, MetricEvent> stream = builder.stream(inputTopic,
                Consumed.with(Serdes.String(), new MetricEventSerde()));

        //2. Group by service + metric name
        KGroupedStream<String, MetricEvent> groupedStream = stream.groupBy(
                (key, value) -> value.getServiceId() + ":" + value.getMetricName(),
                Grouped.with(Serdes.String(), new MetricEventSerde())
        );

        //3. Window: tumbling windows of 1 minute with 5min grace period
        TimeWindows windows = TimeWindows
                .ofSizeWithNoGrace(Duration.ofMinutes(windowSizeMinutes))
                .advanceBy(Duration.ofMinutes(windowSizeMinutes));

        KTable<Windowed<String>, AggregatedMetric> aggregated = groupedStream
                .windowedBy(windows)
                .aggregate(
                        //Initializer
                        () -> AggregatedMetric.builder()
                                .count(0L)
                                .sum(0.0)
                                .min(Double.MIN_VALUE)
                                .max(Double.MAX_VALUE)
                                .build(),
                        //Aggregator
                        (key, value, aggregate) -> {
                            aggregate.setServiceId(value.getServiceId());
                            aggregate.setMetricName(value.getMetricName());
                            aggregate.setMetricType(value.getMetricType());
                            aggregate.setUnit(value.getUnit());
                            aggregate.setCount(aggregate.getCount()+1);
                            aggregate.setSum(aggregate.getSum() + value.getValue());
                            aggregate.setMin(Math.min(aggregate.getMin(), value.getValue()));
                            aggregate.setMax(Math.max(aggregate.getMax(), value.getValue()));
                            aggregate.setAvg(aggregate.getSum()/aggregate.getCount());
                            return aggregate;
                        },
                        //Materialized as state store
                        Materialized.with(Serdes.String(), new AggregatedMetricSerde())
                );

        KStream<String, AggregatedMetric> outputStream = aggregated.toStream()
                .map((windowedKey, value)-> {
                    value.setWindowStart(Instant.ofEpochMilli(windowedKey.window().start()));
                    value.setWindowEnd(Instant.ofEpochMilli(windowedKey.window().end()));

                    //Key: serviceId:metricName:windowStart
                    String outputKey = String.format("%s:%s:%s",
                            value.getServiceId(), value.getMetricName(), windowedKey.window().start());

                    return KeyValue.pair(outputKey, value);
                });

        //5. Write to output topic
        outputStream.to(outputTopic, Produced.with(Serdes.String(), new AggregatedMetricSerde()));

        //Also return the stream for potential future processing
        return stream;
    }

    // Custom Serdes
    private static class MetricEventSerde extends Serdes.WrapperSerde<MetricEvent> {
        public MetricEventSerde() {
            super(new MetricEventSerializer(), new MetricEventDeserializer());
        }
    }

    private static class AggregatedMetricSerde extends Serdes.WrapperSerde<AggregatedMetric> {
        public AggregatedMetricSerde() {
            super(new AggregatedMetricSerializer(), new AggregatedMetricDeserializer());
        }
    }
}
