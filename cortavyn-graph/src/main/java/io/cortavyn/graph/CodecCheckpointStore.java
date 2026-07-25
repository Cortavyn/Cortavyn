package io.cortavyn.graph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Reference encoded store proving the {@link StateCodec} boundary used by durable adapters. */
public final class CodecCheckpointStore implements CheckpointStore {
    private final StateCodec<Checkpoint> codec;
    private final Map<String, byte[]> values = new ConcurrentHashMap<>();
    public CodecCheckpointStore(StateCodec<Checkpoint> codec) { this.codec = java.util.Objects.requireNonNull(codec, "codec must not be null"); }
    @Override public void save(Checkpoint checkpoint) { values.put(checkpoint.id(), codec.encode(checkpoint)); }
    @Override public Optional<Checkpoint> get(String checkpointId) { byte[] value = values.get(checkpointId); return value == null ? Optional.empty() : Optional.of(codec.decode(value)); }
    @Override public List<Checkpoint> history(String threadId) {
        List<Checkpoint> history = new ArrayList<>();
        values.values().stream().map(codec::decode).filter(checkpoint -> checkpoint.threadId().equals(threadId)).sorted(Comparator.comparing(Checkpoint::createdAt)).forEach(history::add);
        return List.copyOf(history);
    }
}
