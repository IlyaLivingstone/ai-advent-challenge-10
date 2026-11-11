package com.ilyalivingstone.aiadvent.day07;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.agent.storage-dir=target/test-sessions")
class SessionStoreTest {

    @Autowired
    private SessionStore store;

    @Test
    void persistsAndRestoresMessages() {
        String id = "test-session-001";
        store.delete(id);
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", "system"),
                Map.of("role", "user", "content", "hello"),
                Map.of("role", "assistant", "content", "hi")
        );
        store.save(id, messages);
        assertThat(store.load(id)).isEqualTo(messages);
        store.delete(id);
        assertThat(store.load(id)).isEmpty();
    }
}
