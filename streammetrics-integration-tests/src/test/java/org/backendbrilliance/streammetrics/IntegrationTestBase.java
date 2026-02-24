package org.backendbrilliance.streammetrics;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext
@EmbeddedKafka(
        partitions = 3,
        topics = {"test-metric-events", "test-metrics-aggregated-1m"},
        brokerProperties = {
//                "listeners=PLAINTEXT://localhost:9093",
//                "port=9093",
                "auto.create.topics.enable=true"
        }
)
public abstract class IntegrationTestBase {

    protected static GenericContainer<?> redisContainer;

    @BeforeAll
    static void startRedisContainer() {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);

        redisContainer.start();

        //Set Redis connection properties
        System.setProperty("spring.redis.host", redisContainer.getHost());
        System.setProperty("spring.redis.port", String.valueOf(redisContainer.getMappedPort(6379)));
    }

    @AfterAll
    static void stopRedisContainer() {
        if(redisContainer!=null && redisContainer.isRunning()) {
            redisContainer.stop();
        }
    }

}
