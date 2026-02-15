package org.backendbrilliance.streammetrics;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = StreammetricsProducerApplication.class)
class StreammetricsProducerApplicationTests {

    @Test
    void contextLoads() {
        System.out.println("loading context for producer");
    }

}
