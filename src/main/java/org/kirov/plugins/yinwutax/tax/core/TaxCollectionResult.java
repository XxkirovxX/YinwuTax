package org.kirov.plugins.yinwutax.tax.core;

import java.math.BigDecimal;

/**
 * 一次税单收取的结果，用于区分「扣款成功」「被免税免除」「扣款失败」三种情况，
 * 以便结算后给出准确的玩家提醒。
 *
 * @param collectedAmount 实际扣除的金额
 * @param exemptionApplied 是否被免税整单免除
 * @param failureReason 扣款失败原因，成功或免税时为 {@code null}
 */
public record TaxCollectionResult(BigDecimal collectedAmount, boolean exemptionApplied, String failureReason) {

    public static TaxCollectionResult collected(BigDecimal amount) {
        return new TaxCollectionResult(amount, false, null);
    }

    public static TaxCollectionResult exempted() {
        return new TaxCollectionResult(BigDecimal.ZERO, true, null);
    }

    public static TaxCollectionResult failed(String reason) {
        return new TaxCollectionResult(BigDecimal.ZERO, false, reason);
    }

    public boolean succeeded() {
        return !exemptionApplied && failureReason == null && collectedAmount.signum() > 0;
    }

    /** 部分扣款：应交多于实扣（例如余额不足），仍算成功但需要如实展示。 */
    public boolean partial(BigDecimal dueAmount) {
        return succeeded() && collectedAmount.compareTo(dueAmount) < 0;
    }
}
