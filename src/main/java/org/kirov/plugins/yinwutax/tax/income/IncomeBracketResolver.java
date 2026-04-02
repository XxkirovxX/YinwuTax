package org.kirov.plugins.yinwutax.tax.income;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.kirov.plugins.yinwutax.config.TaxBracket;

public class IncomeBracketResolver {

    private static final BigDecimal ZERO_RATE = new BigDecimal("0.00");

    private final List<TaxBracket> brackets;

    public IncomeBracketResolver(List<TaxBracket> brackets) {
        this.brackets = List.copyOf(Objects.requireNonNull(brackets, "brackets"));
    }

    public BigDecimal resolveRate(BigDecimal totalIncome) {
        if (totalIncome == null || totalIncome.signum() <= 0) {
            return ZERO_RATE;
        }

        return brackets.stream()
            .filter(bracket -> bracket.matches(totalIncome))
            .map(TaxBracket::rate)
            .findFirst()
            .orElse(ZERO_RATE);
    }
}
