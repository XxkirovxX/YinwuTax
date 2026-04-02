package org.kirov.plugins.yinwutax.tax.wealth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.kirov.plugins.yinwutax.config.TaxBracket;

class WealthBracketResolverTest {

    @Test
    void resolvesZeroRateForEmptyOrNegativeBalance() {
        WealthBracketResolver resolver = new WealthBracketResolver(List.of(
            new TaxBracket(new BigDecimal("0"), new BigDecimal("999.99"), new BigDecimal("0.01"))
        ));

        assertEquals(new BigDecimal("0.00"), resolver.resolveRate(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.00"), resolver.resolveRate(new BigDecimal("-12")));
    }

    @Test
    void resolvesConfiguredWealthBracket() {
        WealthBracketResolver resolver = new WealthBracketResolver(List.of(
            new TaxBracket(new BigDecimal("0"), new BigDecimal("4999.99"), new BigDecimal("0.01")),
            new TaxBracket(new BigDecimal("5000"), new BigDecimal("9999.99"), new BigDecimal("0.03")),
            new TaxBracket(new BigDecimal("10000"), null, new BigDecimal("0.05"))
        ));

        assertEquals(new BigDecimal("0.01"), resolver.resolveRate(new BigDecimal("400")));
        assertEquals(new BigDecimal("0.03"), resolver.resolveRate(new BigDecimal("5000")));
        assertEquals(new BigDecimal("0.05"), resolver.resolveRate(new BigDecimal("50000")));
    }
}
