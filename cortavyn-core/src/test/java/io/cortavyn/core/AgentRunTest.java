package io.cortavyn.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentRunTest {
    @Test
    void snapshotsAttributesImmutably() {
        var run = new AgentRun(new AgentRunId("run-1"), AgentRunState.PENDING, Instant.EPOCH, Map.of("attempt", 1));
        assertEquals(1, run.attributes().get("attempt"));
    }
}
