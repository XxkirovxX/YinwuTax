package org.kirov.plugins.yinwutax.tax.wealth;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

import org.kirov.plugins.yinwutax.integration.EconomyGateway;
import org.kirov.plugins.yinwutax.storage.StorageFacade;
import org.kirov.plugins.yinwutax.storage.TaxDataSnapshot;
import org.kirov.plugins.yinwutax.tax.core.TaxStatement;
import org.kirov.plugins.yinwutax.tax.core.TaxStatementType;

public class WealthTaxService {

    private final WealthBracketResolver bracketResolver;
    private final EconomyGateway economyGateway;
    private final StorageFacade storage;
    private final TaxDataSnapshot snapshot;

    public WealthTaxService(
        WealthBracketResolver bracketResolver,
        EconomyGateway economyGateway,
        StorageFacade storage,
        TaxDataSnapshot snapshot
    ) {
        this.bracketResolver = Objects.requireNonNull(bracketResolver, "bracketResolver");
        this.economyGateway = Objects.requireNonNull(economyGateway, "economyGateway");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public BigDecimal getCurrentRate(UUID playerId) {
        return bracketResolver.resolveRate(economyGateway.getBalance(playerId));
    }

    public TaxStatement prepareStatement(UUID playerId) {
        BigDecimal balance = economyGateway.getBalance(playerId);
        BigDecimal rate = bracketResolver.resolveRate(balance);
        if (balance.signum() <= 0 || rate.signum() <= 0) {
            return null;
        }

        BigDecimal taxAmount = balance.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        if (taxAmount.signum() <= 0) {
            return null;
        }

        return new TaxStatement(TaxStatementType.WEALTH, playerId, balance, rate, taxAmount);
    }

    public void recordCollection(UUID playerId, BigDecimal collectedAmount) {
        snapshot.getLastTaxAmounts().put(playerId, collectedAmount);
        storage.save(snapshot);
    }
}
