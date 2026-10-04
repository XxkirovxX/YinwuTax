package org.kirov.plugins.yinwutax.integration.vaultunlocked;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.kirov.plugins.yinwutax.integration.EconomyCapabilities;
import org.kirov.plugins.yinwutax.integration.EconomyGateway;
import org.kirov.plugins.yinwutax.integration.EconomyTransactionResult;

public class VaultUnlockedEconomyAdapter implements EconomyGateway {

    private static final String PLUGIN_NAME = "YinwuTax";

    private final JavaPlugin plugin;

    public VaultUnlockedEconomyAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public EconomyCapabilities capabilities() {
        Economy provider = resolveProvider();
        return provider == null
            ? new EconomyCapabilities(false, "none", false)
            : new EconomyCapabilities(true, provider.getName(), false);
    }

    @Override
    public BigDecimal getBalance(UUID accountId) {
        Economy provider = resolveProvider();
        return provider == null ? BigDecimal.ZERO : provider.getBalance(PLUGIN_NAME, accountId);
    }

    @Override
    public Collection<UUID> getKnownAccounts() {
        Economy provider = resolveProvider();
        if (provider == null) {
            return Collections.emptyList();
        }

        // 这里直接复用经济插件维护的 UUID->名称映射，避免自己再维护一份账号目录。
        Map<?, ?> map = provider.getUUIDNameMap();
        return map == null ? Collections.emptyList() : map.keySet().stream()
            .filter(UUID.class::isInstance)
            .map(UUID.class::cast)
            .toList();
    }

    @Override
    public EconomyTransactionResult withdrawUpTo(UUID accountId, BigDecimal requestedAmount, String reason) {
        Economy provider = resolveProvider();
        if (provider == null) {
            return new EconomyTransactionResult(false, requestedAmount, BigDecimal.ZERO, BigDecimal.ZERO, "No VaultUnlocked provider available.");
        }

        BigDecimal balance = provider.getBalance(PLUGIN_NAME, accountId);
        // 扣税时只扣到玩家当前可扣余额为止，避免因为余额不足直接整笔失败。
        BigDecimal amountToWithdraw = requestedAmount.min(balance).max(BigDecimal.ZERO);
        if (amountToWithdraw.signum() <= 0) {
            return new EconomyTransactionResult(false, requestedAmount, BigDecimal.ZERO, balance, "No balance available for tax collection.");
        }

        EconomyResponse response = provider.withdraw(PLUGIN_NAME, accountId, amountToWithdraw);
        return new EconomyTransactionResult(
            response.transactionSuccess(),
            requestedAmount,
            response.amount,
            response.balance,
            response.errorMessage
        );
    }

    @Override
    public String format(BigDecimal amount) {
        Economy provider = resolveProvider();
        return provider == null ? amount.toPlainString() : provider.format(amount);
    }

    private Economy resolveProvider() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            plugin.getLogger().fine("VaultUnlocked economy provider not found.");
            return null;
        }

        return registration.getProvider();
    }
}
