package io.cortavyn.model.api;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/** Small in-process metric accumulator that can also be used as a model observer. */
public final class ModelMetrics implements ChatModelObserver {
    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong durationNanos = new AtomicLong();
    private final @Nullable TokenPrice price;
    public ModelMetrics() { this(null); }
    public ModelMetrics(@Nullable TokenPrice price) { this.price = price; }
    @Override public void onComplete(ModelCallEvent event) {
        calls.incrementAndGet(); durationNanos.addAndGet(event.durationNanos());
        if (event.failure() != null) { failures.incrementAndGet(); return; }
        var response = event.response();
        if (response == null) return;
        var usage = response.metadata().usage();
        if (usage != null) { inputTokens.addAndGet(usage.inputTokens()); outputTokens.addAndGet(usage.outputTokens()); }
    }
    public long calls() { return calls.get(); }
    public long failures() { return failures.get(); }
    public long inputTokens() { return inputTokens.get(); }
    public long outputTokens() { return outputTokens.get(); }
    public long durationNanos() { return durationNanos.get(); }
    public BigDecimal estimatedCost() { return price == null ? BigDecimal.ZERO : price.cost(new TokenUsage(Math.toIntExact(inputTokens()), Math.toIntExact(outputTokens()), Math.toIntExact(inputTokens() + outputTokens()))); }
}
