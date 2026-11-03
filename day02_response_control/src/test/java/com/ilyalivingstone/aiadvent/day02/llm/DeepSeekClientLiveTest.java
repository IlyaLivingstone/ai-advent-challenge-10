package com.ilyalivingstone.aiadvent.day02.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_LIVE_TEST", matches = "true")
class DeepSeekClientLiveTest {

    @Autowired
    private DeepSeekClient client;

    @Test
    void completesRealRequest() {
        ChatCompletionResponse response = client.complete(
                "Ответь одним словом: работает",
                1500,
                null,
                false
        );

        assertThat(response.firstChoice()).isNotNull();
        assertThat(response.firstChoice().message().content()).isNotBlank();
        assertThat(response.usage()).isNotNull();
    }
}
