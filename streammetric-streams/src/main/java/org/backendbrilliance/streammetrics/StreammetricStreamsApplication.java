package org.backendbrilliance.streammetrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.EnableKafkaStreams;

@SpringBootApplication
@EnableKafka
@EnableKafkaStreams
public class StreammetricStreamsApplication {

    public static void main(String[] args) {
        SpringApplication.run(StreammetricStreamsApplication.class, args);
    }

}
