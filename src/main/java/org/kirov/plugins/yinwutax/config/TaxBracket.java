package org.kirov.plugins.yinwutax.config;

import java.math.BigDecimal;
import java.util.Objects;

public record TaxBracket(BigDecimal minInclusive, BigDecimal maxInclusive, BigDecimal rate) {

    public TaxBracket {
        Objects.requireNonNull(minInclusive, "minInclusive");
        Objects.requireNonNull(rate, "rate");
    }

    public boolean matches(BigDecimal value) {
        if (value == null) {
            return false;
        }

        if (value.compareTo(minInclusive) < 0) {
            return false;
        }

        return maxInclusive == null || value.compareTo(maxInclusive) <= 0;
    }
}
