package org.kirov.plugins.yinwutax.tax.wealth;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.kirov.plugins.yinwutax.config.TaxBracket;

public class WealthBracketResolver {

    private static final BigDecimal ZERO_RATE = new BigDecimal("0.00");

    private final List<TaxBracket> brackets;

    public WealthBracketResolver(List<TaxBracket> brackets) {
        this.brackets = List.copyOf(Objects.requireNonNull(brackets, "brackets"));
    }

    public BigDecimal resolveRate(BigDecimal balance) {
        if (balance == null || balance.signum() <= 0) {
            return ZERO_RATE;
        }

        return brackets.stream()
            .filter(bracket -> bracket.matches(balance))
            .map(TaxBracket::rate)
            .findFirst()
            .orElse(ZERO_RATE);
    }
}
