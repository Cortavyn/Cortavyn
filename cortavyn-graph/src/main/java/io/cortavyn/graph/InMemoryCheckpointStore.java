package io.cortavyn.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe reference checkpoint store for tests, examples, and ephemeral processes. */
public final class InMemoryCheckpointStore implements CheckpointStore {
    private final Map<String, Checkpoint> checkpoints = new ConcurrentHashMap<>();
    @Override public void save(Checkpoint checkpoint) { checkpoints.put(checkpoint.id(), checkpoint); }
    @Override public Optional<Checkpoint> get(String checkpointId) { return Optional.ofNullable(checkpoints.get(checkpointId)); }
    @Override public List<Checkpoint> history(String threadId) {
        List<Checkpoint> result = new ArrayList<>();
        checkpoints.values().stream().filter(c -> c.threadId().equals(threadId)).sorted(Comparator.comparing(Checkpoint::createdAt)).forEach(result::add);
        return List.copyOf(result);
    }
}
