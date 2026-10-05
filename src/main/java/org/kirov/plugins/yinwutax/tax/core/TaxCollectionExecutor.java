package org.kirov.plugins.yinwutax.tax.core;

import java.util.Objects;

import org.kirov.plugins.yinwutax.integration.EconomyGateway;
import org.kirov.plugins.yinwutax.integration.EconomyTransactionResult;
import org.kirov.plugins.yinwutax.notify.TaxNotifier;
import org.kirov.plugins.yinwutax.tax.exemption.ExemptionService;

/**
 * 执行单张税单的收取，并负责把结果同步给纳税人。
 *
 * <p>收取动作与提醒放在一起，是为了让提醒必然反映真实结果：
 * 扣款成功、被免税整单免除、扣款失败（含部分扣款）三种情况分别给不同文案。
 */
public class TaxCollectionExecutor {

    private static final String INCOME_TAX_LABEL = "所得税";
    private static final String WEALTH_TAX_LABEL = "财富税";

    private final EconomyGateway economyGateway;
    private final ExemptionService exemptionService;
    private final boolean exemptionEnabled;
    private final TaxNotifier notifier;

    public TaxCollectionExecutor(
        EconomyGateway economyGateway,
        ExemptionService exemptionService,
        boolean exemptionEnabled,
        TaxNotifier notifier
    ) {
        this.economyGateway = Objects.requireNonNull(economyGateway, "economyGateway");
        this.exemptionService = Objects.requireNonNull(exemptionService, "exemptionService");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.exemptionEnabled = exemptionEnabled;
    }

    public TaxCollectionResult execute(TaxStatement statement) {
        TaxCollectionResult result = collect(statement);
        notifyPlayer(statement, result);
        return result;
    }

    private TaxCollectionResult collect(TaxStatement statement) {
        if (exemptionEnabled && exemptionService.consumeForCurrentStatement(statement.playerId())) {
            // 免税是整单免除：不扣任何钱，也不做部分减免。
            return TaxCollectionResult.exempted();
        }

        EconomyTransactionResult result = economyGateway.withdrawUpTo(
            statement.playerId(),
            statement.taxAmount(),
            statement.type().name().toLowerCase() + "_tax"
        );

        if (!result.success() || result.withdrawnAmount().signum() <= 0) {
            return TaxCollectionResult.failed(result.errorMessage());
        }

        return TaxCollectionResult.collected(result.withdrawnAmount());
    }

    private void notifyPlayer(TaxStatement statement, TaxCollectionResult result) {
        notifier.notifySettlement(
            label(statement),
            statement.playerId(),
            statement.taxableBase(),
            statement.taxRate(),
            statement.taxAmount(),
            result.collectedAmount(),
            result.exemptionApplied(),
            result.failureReason()
        );
    }

    private String label(TaxStatement statement) {
        return statement.type() == TaxStatementType.WEALTH ? WEALTH_TAX_LABEL : INCOME_TAX_LABEL;
    }
}
