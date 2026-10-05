package org.kirov.plugins.yinwutax.tax.core;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.kirov.plugins.yinwutax.integration.EconomyGateway;
import org.kirov.plugins.yinwutax.tax.headcount.HeadcountTaxService;
import org.kirov.plugins.yinwutax.tax.headcount.IpHistoryTracker;
import org.kirov.plugins.yinwutax.tax.income.IncomeTaxService;
import org.kirov.plugins.yinwutax.tax.wealth.WealthTaxService;

public class TaxCoordinator {

    private final EconomyGateway economyGateway;
    private final IncomeTaxService incomeTaxService;
    private final HeadcountTaxService headcountTaxService;
    private final IpHistoryTracker ipHistoryTracker;
    private final WealthTaxService wealthTaxService;
    private final TaxCollectionExecutor collectionExecutor;
    private final boolean headcountEnabled;

    public TaxCoordinator(
        EconomyGateway economyGateway,
        IncomeTaxService incomeTaxService,
        HeadcountTaxService headcountTaxService,
        IpHistoryTracker ipHistoryTracker,
        WealthTaxService wealthTaxService,
        TaxCollectionExecutor collectionExecutor,
        boolean headcountEnabled
    ) {
        this.economyGateway = economyGateway;
        this.incomeTaxService = incomeTaxService;
        this.headcountTaxService = headcountTaxService;
        this.ipHistoryTracker = ipHistoryTracker;
        this.wealthTaxService = wealthTaxService;
        this.collectionExecutor = collectionExecutor;
        this.headcountEnabled = headcountEnabled;
    }

    public int settleIncomeTaxes() {
        int settled = 0;
        for (UUID playerId : incomeTaxService.getTrackedPlayers()) {
            BigDecimal baseRate = incomeTaxService.getCurrentBaseRate(playerId);
            // 规则顺序固定：先算所得税梯度，再乘人头税附加倍率。
            BigDecimal finalRate = headcountEnabled
                ? headcountTaxService.resolveFinalIncomeTaxRate(playerId, baseRate, ipHistoryTracker.getLinkedAccountCount(playerId))
                : baseRate;
            TaxStatement statement = incomeTaxService.prepareStatement(playerId, finalRate);
            if (statement == null) {
                incomeTaxService.finishCycle(playerId, BigDecimal.ZERO);
                continue;
            }

            TaxCollectionResult result = collectionExecutor.execute(statement);
            incomeTaxService.finishCycle(playerId, result.collectedAmount());
            if (result.exemptionApplied() || result.succeeded()) {
                settled++;
            }
        }
        return settled;
    }

    public int settleWealthTaxes() {
        // 财富税直接从经济插件读取当前余额，因此先拿到当前已知账户集合。
        Set<UUID> accounts = new HashSet<>(economyGateway.getKnownAccounts());
        incomeTaxService.registerKnownAccounts(accounts);

        int settled = 0;
        for (UUID playerId : accounts) {
            TaxStatement statement = wealthTaxService.prepareStatement(playerId);
            if (statement == null) {
                continue;
            }

            TaxCollectionResult result = collectionExecutor.execute(statement);
            wealthTaxService.recordCollection(playerId, result.collectedAmount());
            if (result.exemptionApplied() || result.succeeded()) {
                settled++;
            }
        }
        return settled;
    }
}
