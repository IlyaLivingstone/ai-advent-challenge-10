package com.ilyalivingstone.aiadvent.day02.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_LIVE_TEST", matches = "true")
class ExperimentServiceLiveTest {

    @Autowired
    private ExperimentService experimentService;

    @Test
    void runsEveryVariantOnce() {
        ExperimentReport report = experimentService.run(1);

        assertThat(report.variants()).hasSize(5);
        assertThat(report.totalRequests()).isEqualTo(5);
        assertThat(report.variants()).allSatisfy(variant -> {
            assertThat(variant.runs()).hasSize(1);
            RunResult run = variant.runs().getFirst();
            System.out.printf(
                    "%s: finish=%s, tokens=%s, directJson=%s, schemaValid=%s, findings=%s%n",
                    variant.spec().variant().id(),
                    run.finishReason(),
                    run.completionTokens(),
                    run.analysis().directJson(),
                    run.analysis().schemaValid(),
                    variant.summary().findings()
            );
        });
    }
}
