package org.kirov.plugins.yinwutax.integration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.Plugin;

/**
 * 通过反射访问经济 API 的网关，同时支持 VaultUnlocked（{@code net.milkbowl.vault2.economy.Economy}）
 * 与经典 Vault（{@code net.milkbowl.vault.economy.Economy}）。
 *
 * <p>本类<b>刻意不引用任何 Vault 类型</b>，全部通过接口名解析、动态代理与方法查找完成：
 * 目标插件缺失时只会得到 "provider 不存在" 的正常结论，而不是
 * {@link NoClassDefFoundError}（那是 {@link Error}，业务代码的 {@code catch (Exception)} 抓不住，
 * 会一路冒到命令层导致玩家看到命令异常）。
 */
public final class ReflectiveEconomyGateway implements EconomyGateway {

    /** 记账主体名，VaultUnlocked 要求传入插件名。 */
    private static final String PLUGIN_NAME = "YinwuTax";

    private final Plugin plugin;
    private final String apiName;
    private final String providerName;
    private final Object provider;
    private final List<Method> balanceMethods;
    private final List<Method> withdrawMethods;
    private final Method formatMethod;

    private ReflectiveEconomyGateway(
        Plugin plugin,
        String apiName,
        String providerName,
        Object provider,
        List<Method> balanceMethods,
        List<Method> withdrawMethods,
        Method formatMethod
    ) {
        this.plugin = plugin;
        this.apiName = apiName;
        this.providerName = providerName;
        this.provider = provider;
        this.balanceMethods = balanceMethods;
        this.withdrawMethods = withdrawMethods;
        this.formatMethod = formatMethod;
    }

    /**
     * 按接口名解析经济 provider；接口不存在或没有注册 provider 时返回 {@code null}。
     */
    static ReflectiveEconomyGateway create(Plugin plugin, String apiName) {
        return create(plugin, apiName, ReflectiveEconomyGateway::findRegisteredProvider);
    }

    /**
     * 供测试注入 provider 来源：无服务端环境下 {@code Bukkit.getServicesManager()} 不可用。
     */
    static ReflectiveEconomyGateway create(Plugin plugin, String apiName, ProviderLookup providerLookup) {
        Class<?> api = loadApi(apiName);
        if (api == null) {
            return null;
        }

        Object provider = providerLookup.lookup(api);
        if (provider == null) {
            return null;
        }

        List<Method> balanceMethods = findMethods(api, "getBalance");
        if (balanceMethods.isEmpty()) {
            plugin.getLogger().warning(
                "Economy API " + apiName + " does not expose getBalance; YinwuTax will run without economy access."
            );
            return null;
        }

        return new ReflectiveEconomyGateway(
            plugin,
            apiName,
            resolveProviderName(api, provider, apiName),
            provider,
            balanceMethods,
            findMethods(api, "withdraw"),
            findMethod(api, "format", BigDecimal.class).orElse(null)
        );
    }

    /** provider 查询策略，便于测试注入。 */
    @FunctionalInterface
    interface ProviderLookup {

        Object lookup(Class<?> api);
    }

    /** 供测试构造假 provider：把动态代理转成指定 API 接口。 */
    static Object proxyOf(Class<?> api, InvocationHandler handler) {
        return Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, handler);
    }

    @Override
    public EconomyCapabilities capabilities() {
        return new EconomyCapabilities(true, providerName, false);
    }

    @Override
    public BigDecimal getBalance(UUID accountId) {
        return invokeBalance(accountId, BigDecimal.ZERO);
    }

    @Override
    public Collection<UUID> getKnownAccounts() {
        Optional<Method> uuidNameMap = findMethod(provider.getClass(), "getUUIDNameMap");
        if (uuidNameMap.isEmpty()) {
            return Collections.emptyList();
        }

        Object result = invokeQuietly(uuidNameMap.get());
        if (!(result instanceof Map<?, ?> map)) {
            return Collections.emptyList();
        }

        return map.keySet().stream()
            .filter(UUID.class::isInstance)
            .map(UUID.class::cast)
            .toList();
    }

    @Override
    public EconomyTransactionResult withdrawUpTo(UUID accountId, BigDecimal requestedAmount, String reason) {
        BigDecimal balance = getBalance(accountId);
        // 扣税只扣到当前可扣余额为止，避免余额不足时整笔失败。
        BigDecimal amountToWithdraw = requestedAmount.min(balance).max(BigDecimal.ZERO);
        if (amountToWithdraw.signum() <= 0) {
            return new EconomyTransactionResult(
                false,
                requestedAmount,
                BigDecimal.ZERO,
                balance,
                "No balance available for tax collection."
            );
        }

        if (withdrawMethods.isEmpty()) {
            return new EconomyTransactionResult(
                false,
                requestedAmount,
                BigDecimal.ZERO,
                balance,
                "Economy provider does not support withdraw."
            );
        }

        Object response = invokeFirst(withdrawMethods, accountId, amountToWithdraw);
        if (response == null) {
            return new EconomyTransactionResult(
                false,
                requestedAmount,
                BigDecimal.ZERO,
                balance,
                "Economy withdraw failed, see server log for details."
            );
        }

        return new EconomyTransactionResult(
            Boolean.TRUE.equals(readProperty(response, "transactionSuccess", Boolean.class)),
            requestedAmount,
            readProperty(response, "amount", BigDecimal.class, BigDecimal.ZERO),
            readProperty(response, "balance", BigDecimal.class, balance),
            readProperty(response, "errorMessage", String.class, null)
        );
    }

    @Override
    public String format(BigDecimal amount) {
        if (formatMethod == null) {
            return amount.toPlainString();
        }

        Object formatted = invoke(formatMethod, amount);
        return formatted instanceof String text ? text : amount.toPlainString();
    }

    /** 供日志与状态展示使用。 */
    String apiName() {
        return apiName;
    }

    private BigDecimal invokeBalance(UUID accountId, BigDecimal fallback) {
        if (provider == null) {
            return fallback;
        }

        Object result = invokeFirst(balanceMethods, accountId, BigDecimal.ZERO);
        if (result instanceof BigDecimal decimal) {
            return decimal;
        }

        return result instanceof Number number ? BigDecimal.valueOf(number.doubleValue()) : fallback;
    }

    /**
     * 按候选签名逐个尝试调用：Vault 1 与 Vault 2 的同名方法参数形态不同，
     * 单看名字无法区分，因此这里让运行时来挑。
     */
    private Object invokeFirst(List<Method> candidates, UUID accountId, BigDecimal amount) {
        for (Method method : candidates) {
            Object[] arguments = adaptArguments(method, accountId, amount);
            if (arguments == null) {
                continue;
            }

            Object result = invoke(method, arguments);
            if (result != null || method.getReturnType() == void.class) {
                return result;
            }
        }

        return null;
    }

    /**
     * 依据参数类型把 {@code accountId} / 插件名 / 金额换算成实参；无法适配的签名返回 {@code null}。
     */
    private Object[] adaptArguments(Method method, UUID accountId, BigDecimal amount) {
        Class<?>[] types = method.getParameterTypes();
        Object[] arguments = new Object[types.length];

        for (int index = 0; index < types.length; index++) {
            Class<?> type = types[index];
            if (UUID.class.isAssignableFrom(type)) {
                arguments[index] = accountId;
            } else if (OfflinePlayer.class.isAssignableFrom(type)) {
                arguments[index] = Bukkit.getOfflinePlayer(accountId);
            } else if (BigDecimal.class.isAssignableFrom(type)) {
                arguments[index] = amount;
            } else if (String.class.isAssignableFrom(type)) {
                arguments[index] = PLUGIN_NAME;
            } else {
                return null;
            }
        }

        return arguments;
    }

    private Object invoke(Method method, Object... arguments) {
        try {
            return method.invoke(provider, arguments);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException failure) {
            Throwable cause = failure instanceof InvocationTargetException && failure.getCause() != null
                ? failure.getCause()
                : failure;
            plugin.getLogger().log(
                Level.FINE,
                "Failed to invoke " + method.getName() + " on economy provider " + providerName,
                cause
            );
            return null;
        }
    }

    private Object invokeQuietly(Method method) {
        return invoke(method);
    }

    private <T> T readProperty(Object target, String name, Class<T> type) {
        return readProperty(target, name, type, null);
    }

    /**
     * 读取 {@code EconomyResponse} 的字段或方法。VaultUnlocked 里 {@code amount}/{@code balance}/
     * {@code errorMessage} 是公有字段，{@code transactionSuccess()} 是方法，因此两种都要试。
     */
    private <T> T readProperty(Object target, String name, Class<T> type, T fallback) {
        Object value = readMember(target, name, true);
        if (value == null) {
            value = readMember(target, name, false);
        }

        return type.isInstance(value) ? type.cast(value) : fallback;
    }

    private Object readMember(Object target, String name, boolean asMethod) {
        try {
            if (asMethod) {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            }

            return target.getClass().getField(name).get(target);
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null;
        }
    }

    private static Class<?> loadApi(String apiName) {
        try {
            return Class.forName(apiName, false, ReflectiveEconomyGateway.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    static Object findRegisteredProvider(Class<?> api) {
        RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(api);
        return registration == null ? null : registration.getProvider();
    }

    private static String resolveProviderName(Class<?> api, Object provider, String fallback) {
        try {
            Object name = api.getMethod("getName").invoke(provider);
            return name instanceof String text && !text.isBlank() ? text : fallback;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return fallback;
        }
    }

    private static List<Method> findMethods(Class<?> api, String name) {
        return Arrays.stream(api.getMethods())
            .filter(method -> method.getName().equals(name))
            .toList();
    }

    private static Optional<Method> findMethod(Class<?> api, String name, Class<?>... parameterTypes) {
        try {
            return Optional.of(api.getMethod(name, parameterTypes));
        } catch (NoSuchMethodException absent) {
            return Optional.empty();
        }
    }
}
