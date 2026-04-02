package org.kirov.plugins.yinwutax.tax.headcount;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

public class HeadcountTaxService {

    private final HeadcountOverrideStore overrideStore;
    private final BigDecimal perAccountExtraRate;

    public HeadcountTaxService(HeadcountOverrideStore overrideStore, BigDecimal perAccountExtraRate) {
        this.overrideStore = Objects.requireNonNull(overrideStore, "overrideStore");
        this.perAccountExtraRate = Objects.requireNonNull(perAccountExtraRate, "perAccountExtraRate");
    }

    public BigDecimal resolveFinalIncomeTaxRate(UUID playerId, BigDecimal baseIncomeTaxRate, int linkedAccountCount) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(baseIncomeTaxRate, "baseIncomeTaxRate");

        int extraAccounts = overrideStore.getOverrideExtraAccounts(playerId)
            .orElseGet(() -> Math.max(0, linkedAccountCount - 1));

        BigDecimal multiplier = BigDecimal.ONE.add(perAccountExtraRate.multiply(BigDecimal.valueOf(extraAccounts)));
        return baseIncomeTaxRate.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);
    }
}
