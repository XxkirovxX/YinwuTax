package org.kirov.plugins.yinwutax.tax.core;

import java.math.BigDecimal;
import java.util.Objects;

import org.kirov.plugins.yinwutax.integration.EconomyGateway;
import org.kirov.plugins.yinwutax.integration.EconomyTransactionResult;
import org.kirov.plugins.yinwutax.tax.exemption.ExemptionService;

public class TaxCollectionExecutor {

    private final EconomyGateway economyGateway;
    private final ExemptionService exemptionService;
    private final boolean exemptionEnabled;

    public TaxCollectionExecutor(EconomyGateway economyGateway, ExemptionService exemptionService, boolean exemptionEnabled) {
        this.economyGateway = Objects.requireNonNull(economyGateway, "economyGateway");
        this.exemptionService = Objects.requireNonNull(exemptionService, "exemptionService");
        this.exemptionEnabled = exemptionEnabled;
    }

    public BigDecimal execute(TaxStatement statement) {
        if (exemptionEnabled && exemptionService.consumeForCurrentStatement(statement.playerId())) {
            return BigDecimal.ZERO;
        }

        EconomyTransactionResult result = economyGateway.withdrawUpTo(
            statement.playerId(),
            statement.taxAmount(),
            statement.type().name().toLowerCase() + "_tax"
        );
        return result.withdrawnAmount();
    }
}
