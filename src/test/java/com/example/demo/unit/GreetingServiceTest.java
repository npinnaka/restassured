package com.example.demo.unit;

import com.example.demo.model.GreetingRequest;
import com.example.demo.model.GreetingResponse;
import com.example.demo.service.GreetingService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GreetingServiceTest {

    private final GreetingService service = new GreetingService();

    @Test
    void greetBuildsMessage() {
        GreetingResponse response = service.greet(new GreetingRequest("World"), "user");

        assertThat(response.message()).isEqualTo("Hello, World!");
        assertThat(response.requestedBy()).isEqualTo("user");
    }
}
