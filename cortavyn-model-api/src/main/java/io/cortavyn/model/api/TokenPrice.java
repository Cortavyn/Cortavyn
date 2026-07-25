package io.cortavyn.model.api;

import java.math.BigDecimal;
import java.util.Objects;

/** Price in a chosen currency per one million input/output tokens. */
public record TokenPrice(String currency, BigDecimal inputPerMillion, BigDecimal outputPerMillion) {
    public TokenPrice {
        Objects.requireNonNull(currency, "currency must not be null");
        if (currency.isBlank()) throw new IllegalArgumentException("currency must not be blank");
        inputPerMillion = nonNegative(inputPerMillion, "inputPerMillion");
        outputPerMillion = nonNegative(outputPerMillion, "outputPerMillion");
    }
    public BigDecimal cost(TokenUsage usage) {
        Objects.requireNonNull(usage, "usage must not be null");
        return inputPerMillion.multiply(BigDecimal.valueOf(usage.inputTokens())).add(outputPerMillion.multiply(BigDecimal.valueOf(usage.outputTokens())))
                .movePointLeft(6);
    }
    private static BigDecimal nonNegative(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() < 0) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }
}
