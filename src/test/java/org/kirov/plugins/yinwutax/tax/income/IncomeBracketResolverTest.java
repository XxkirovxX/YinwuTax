package org.kirov.plugins.yinwutax.tax.income;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kirov.plugins.yinwutax.config.TaxBracket;

class IncomeBracketResolverTest {

    @Test
    void returnsZeroRateWhenIncomeIsNotPositive() {
        IncomeBracketResolver resolver = new IncomeBracketResolver(List.of(
            new TaxBracket(new BigDecimal("0"), new BigDecimal("999.99"), new BigDecimal("0.03"))
        ));

        assertEquals(new BigDecimal("0.00"), resolver.resolveRate(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.00"), resolver.resolveRate(new BigDecimal("-1")));
    }

    @Test
    void resolvesMatchingBracketForIncome() {
        IncomeBracketResolver resolver = new IncomeBracketResolver(List.of(
            new TaxBracket(new BigDecimal("0"), new BigDecimal("99.99"), new BigDecimal("0.03")),
            new TaxBracket(new BigDecimal("100"), new BigDecimal("999.99"), new BigDecimal("0.10")),
            new TaxBracket(new BigDecimal("1000"), null, new BigDecimal("0.20"))
        ));

        assertEquals(new BigDecimal("0.03"), resolver.resolveRate(new BigDecimal("50")));
        assertEquals(new BigDecimal("0.10"), resolver.resolveRate(new BigDecimal("500")));
        assertEquals(new BigDecimal("0.20"), resolver.resolveRate(new BigDecimal("2500")));
    }
}
