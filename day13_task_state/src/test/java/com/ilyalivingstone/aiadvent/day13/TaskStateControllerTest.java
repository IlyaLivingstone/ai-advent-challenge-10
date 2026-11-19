package com.ilyalivingstone.aiadvent.day13;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.agent.storage-dir=target/test-day13-task-controller-sessions",
        "app.profile.dir=target/test-day13-task-controller-profiles",
        "app.memory.dir=target/test-day13-task-controller-memory"
})
@AutoConfigureMockMvc
class TaskStateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsInitialStateAndAllowedTransitions() throws Exception {
        String sessionId = createSession();

        mockMvc.perform(get("/api/agent/task/state").param("sessionId", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.stage").value("planning"))
                .andExpect(jsonPath("$.state.currentStep").isNotEmpty())
                .andExpect(jsonPath("$.state.expectedAction").isNotEmpty())
                .andExpect(jsonPath("$.state.paused").value(false))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("execution"));
    }

    @Test
    void allowedTransitionPauseAndResumeFlow() throws Exception {
        String sessionId = createSession();

        mockMvc.perform(post("/api/agent/task/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(sessionId, "execution", "Написать автомат")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.stage").value("execution"))
                .andExpect(jsonPath("$.state.currentStep").value("Написать автомат"))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("validation"))
                .andExpect(jsonPath("$.history[0].action").value("transition"))
                .andExpect(jsonPath("$.history[0].from").value("planning"))
                .andExpect(jsonPath("$.history[0].to").value("execution"));

        mockMvc.perform(post("/api/agent/task/pause")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.paused").value(true))
                .andExpect(jsonPath("$.state.stage").value("execution"));

        mockMvc.perform(post("/api/agent/task/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(sessionId, "validation", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("на паузе")))
                .andExpect(jsonPath("$.stateChanged").value(false));

        mockMvc.perform(post("/api/agent/task/resume")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.paused").value(false));

        mockMvc.perform(post("/api/agent/task/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(sessionId, "validation", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.stage").value("validation"))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("done"));

        mockMvc.perform(post("/api/agent/task/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(sessionId, "done", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.stage").value("done"))
                .andExpect(jsonPath("$.allowedTransitions").isEmpty());

        mockMvc.perform(post("/api/agent/task/pause")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionBody(sessionId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("Пауза недоступна")));
    }

    @Test
    void forbiddenTransitionReturnsConflictAndKeepsState() throws Exception {
        String sessionId = createSession();

        mockMvc.perform(post("/api/agent/task/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(sessionId, "validation", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("Недопустимый переход")))
                .andExpect(jsonPath("$.error", containsString("planning → validation")))
                .andExpect(jsonPath("$.error", containsString("Состояние не изменено")))
                .andExpect(jsonPath("$.from").value("planning"))
                .andExpect(jsonPath("$.to").value("validation"))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("execution"))
                .andExpect(jsonPath("$.stateChanged").value(false));

        mockMvc.perform(get("/api/agent/task/state").param("sessionId", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.stage").value("planning"))
                .andExpect(jsonPath("$.history").isEmpty());
    }

    @Test
    void unknownStageReturnsBadRequest() throws Exception {
        String sessionId = createSession();

        mockMvc.perform(post("/api/agent/task/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transitionBody(sessionId, "review", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Неизвестный этап")))
                .andExpect(jsonPath("$.error", containsString("planning")))
                .andExpect(jsonPath("$.stateChanged").value(false));
    }

    @Test
    void exposesMachineTransitionsMetadata() throws Exception {
        mockMvc.perform(get("/api/agent/task/transitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialStage").value("planning"))
                .andExpect(jsonPath("$.stages.length()").value(4))
                .andExpect(jsonPath("$.transitions.length()").value(4))
                .andExpect(jsonPath("$.transitions[0].from").value("planning"))
                .andExpect(jsonPath("$.transitions[0].to").value("execution"))
                .andExpect(jsonPath("$.pauseAllowedOn.length()").value(3));
    }

    private String createSession() throws Exception {
        String body = mockMvc.perform(post("/api/agent/sessions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.sessionId");
    }

    private String transitionBody(String sessionId, String stage, String currentStep) {
        String stepPart = currentStep == null ? "" : ",\"currentStep\":\"" + currentStep + "\"";
        return "{\"sessionId\":\"" + sessionId + "\",\"stage\":\"" + stage + "\"" + stepPart + "}";
    }

    private String actionBody(String sessionId) {
        return "{\"sessionId\":\"" + sessionId + "\"}";
    }
}
