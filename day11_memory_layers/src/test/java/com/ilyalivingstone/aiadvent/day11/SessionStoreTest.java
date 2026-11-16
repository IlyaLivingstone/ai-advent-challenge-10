package com.ilyalivingstone.aiadvent.day11;

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
        store.save(id, messages, "summary", 2, true, TokenStats.empty(128000), ContextStrategy.SUMMARY, java.util.Map.of(), java.util.Map.of(), true, true, true);
        assertThat(store.load(id).messages()).isEqualTo(messages);
        assertThat(store.load(id).summary()).isEqualTo("summary");
        store.delete(id);
        assertThat(store.load(id)).isNull();
    }
}
