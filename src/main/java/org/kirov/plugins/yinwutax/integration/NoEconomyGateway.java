package org.kirov.plugins.yinwutax.integration;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

/**
 * 没有任何经济插件时使用的网关。
 *
 * <p>它的存在是为了让插件在缺少经济依赖时仍能启动、命令仍能执行：
 * 余额一律读作 0，扣税一律失败并给出可读原因，而不是抛异常把命令打断。
 */
final class NoEconomyGateway implements EconomyGateway {

    private static final String REASON = "No economy provider available. Install VaultUnlocked or Vault.";

    @Override
    public EconomyCapabilities capabilities() {
        return new EconomyCapabilities(false, "none", false);
    }

    @Override
    public BigDecimal getBalance(UUID accountId) {
        return BigDecimal.ZERO;
    }

    @Override
    public Collection<UUID> getKnownAccounts() {
        return Collections.emptyList();
    }

    @Override
    public EconomyTransactionResult withdrawUpTo(UUID accountId, BigDecimal requestedAmount, String reason) {
        return new EconomyTransactionResult(false, requestedAmount, BigDecimal.ZERO, BigDecimal.ZERO, REASON);
    }

    @Override
    public String format(BigDecimal amount) {
        return amount.toPlainString();
    }
}
