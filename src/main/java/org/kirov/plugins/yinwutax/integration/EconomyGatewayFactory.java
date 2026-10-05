package org.kirov.plugins.yinwutax.integration;

import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

/**
 * 选择可用的经济网关。
 *
 * <p>解析顺序：VaultUnlocked（{@code net.milkbowl.vault2}）→ 经典 Vault（{@code net.milkbowl.vault}）
 * → 无经济插件。
 *
 * <p>之所以把解析过程集中在这里并吞掉 {@link LinkageError}：缺少 VaultUnlocked 时，
 * 任何直接引用其类型的类都会抛 {@code NoClassDefFoundError}，而那是 {@link Error}，
 * 业务代码的 {@code catch (Exception)} 抓不住，会一路冒到命令层变成 "Command exception"。
 * 这里把"依赖缺失"收敛成一次明确的日志 + 安全降级。
 */
public final class EconomyGatewayFactory {

    /** VaultUnlocked。 */
    public static final String VAULT_UNLOCKED_API = "net.milkbowl.vault2.economy.Economy";

    /** 经典 Vault 1.x。 */
    public static final String VAULT_API = "net.milkbowl.vault.economy.Economy";

    private EconomyGatewayFactory() {
    }

    public static EconomyGateway create(Plugin plugin) {
        return create(plugin, VAULT_UNLOCKED_API, VAULT_API);
    }

    /**
     * @param apiCandidates 按优先级排列的经济 API 接口名，便于测试注入。
     */
    static EconomyGateway create(Plugin plugin, String... apiCandidates) {
        return create(plugin, ReflectiveEconomyGateway::findRegisteredProvider, apiCandidates);
    }

    /**
     * 供测试注入 provider 来源：无服务端环境下 {@code Bukkit.getServicesManager()} 不可用。
     */
    static EconomyGateway create(
        Plugin plugin,
        ReflectiveEconomyGateway.ProviderLookup providerLookup,
        String... apiCandidates
    ) {
        for (String apiName : apiCandidates) {
            ReflectiveEconomyGateway gateway = createGateway(plugin, apiName, providerLookup);
            if (gateway != null) {
                plugin.getLogger().info("Using economy provider '" + gateway.capabilities().providerName()
                    + "' via " + apiName + ".");
                return gateway;
            }
        }

        plugin.getLogger().warning(
            "No economy provider found (looked for " + String.join(", ", apiCandidates) + "). "
                + "YinwuTax will start, but balances read as 0 and no tax can be collected. "
                + "Install VaultUnlocked (or Vault + an economy plugin) to enable taxation."
        );
        return new NoEconomyGateway();
    }

    private static ReflectiveEconomyGateway createGateway(
        Plugin plugin,
        String apiName,
        ReflectiveEconomyGateway.ProviderLookup providerLookup
    ) {
        try {
            return ReflectiveEconomyGateway.create(plugin, apiName, providerLookup);
        } catch (LinkageError missing) {
            // 目标 API 的 jar 不在服务端：属于预期情况，换个候选继续，不再上抛。
            plugin.getLogger().log(Level.FINE, "Economy API " + apiName + " is not available", missing);
            return null;
        } catch (RuntimeException unexpected) {
            plugin.getLogger().log(
                Level.WARNING,
                "Failed to initialise economy access via " + apiName + "; continuing without it.",
                unexpected
            );
            return null;
        }
    }
}
