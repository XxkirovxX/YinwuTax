package org.kirov.plugins.yinwutax.tax.income;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.kirov.plugins.yinwutax.storage.StorageFacade;
import org.kirov.plugins.yinwutax.storage.TaxDataSnapshot;
import org.kirov.plugins.yinwutax.tax.core.TaxStatement;
import org.kirov.plugins.yinwutax.tax.core.TaxStatementType;

public class IncomeTaxService {

    private final IncomeBracketResolver bracketResolver;
    private final StorageFacade storage;
    private final TaxDataSnapshot snapshot;

    public IncomeTaxService(IncomeBracketResolver bracketResolver, StorageFacade storage, TaxDataSnapshot snapshot) {
        this.bracketResolver = Objects.requireNonNull(bracketResolver, "bracketResolver");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    }

    public void recordIncome(UUID playerId, BigDecimal amount) {
        if (playerId == null || amount == null || amount.signum() <= 0) {
            return;
        }

        snapshot.getKnownAccounts().add(playerId);
        snapshot.getIncomeTotals().merge(playerId, amount, BigDecimal::add);
    }

    public BigDecimal getAccruedIncome(UUID playerId) {
        return snapshot.getIncomeTotals().getOrDefault(playerId, BigDecimal.ZERO);
    }

    public BigDecimal getCurrentBaseRate(UUID playerId) {
        return bracketResolver.resolveRate(getAccruedIncome(playerId));
    }

    public Set<UUID> getTrackedPlayers() {
        return Set.copyOf(snapshot.getIncomeTotals().keySet());
    }

    public void registerKnownAccounts(Collection<UUID> accountIds) {
        snapshot.getKnownAccounts().addAll(accountIds);
    }

    public TaxStatement prepareStatement(UUID playerId, BigDecimal finalTaxRate) {
        BigDecimal taxableIncome = getAccruedIncome(playerId);
        if (taxableIncome.signum() <= 0 || finalTaxRate.signum() <= 0) {
            return null;
        }

        BigDecimal taxAmount = taxableIncome.multiply(finalTaxRate).setScale(2, RoundingMode.HALF_UP);
        if (taxAmount.signum() <= 0) {
            return null;
        }

        return new TaxStatement(TaxStatementType.INCOME, playerId, taxableIncome, finalTaxRate, taxAmount);
    }

    public void finishCycle(UUID playerId, BigDecimal collectedAmount) {
        snapshot.getIncomeTotals().remove(playerId);
        snapshot.getLastTaxAmounts().put(playerId, collectedAmount);
        storage.save(snapshot);
    }
}
