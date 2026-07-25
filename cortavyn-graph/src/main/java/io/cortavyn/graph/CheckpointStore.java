package io.cortavyn.graph;

import java.util.List;
import java.util.Optional;

/** Storage SPI for checkpoint history. Production stores define their own encoding via {@link StateCodec}. */
public interface CheckpointStore {
    void save(Checkpoint checkpoint);
    Optional<Checkpoint> get(String checkpointId);
    List<Checkpoint> history(String threadId);
}
