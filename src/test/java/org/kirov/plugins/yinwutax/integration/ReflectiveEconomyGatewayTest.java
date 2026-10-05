package org.kirov.plugins.yinwutax.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/**
 * 经济网关测试。
 *
 * <p>这些用例覆盖的是生产上真实踩到的故障：服务端缺少 VaultUnlocked 时，
 * 直接引用其类型的代码会抛 {@code NoClassDefFoundError}，导致命令层出现未捕获异常。
 * 网关必须把这种情况收敛成"安全降级 + 明确日志"。
 */
class ReflectiveEconomyGatewayTest {

    private static final String VAULT2_API = "net.milkbowl.vault2.economy.Economy";
    private static final String MISSING_API = "org.example.notinstalled.Economy";

    @Test
    void missingApiClassYieldsNoGatewayInsteadOfThrowing() {
        assertNull(ReflectiveEconomyGateway.create(fakePlugin(), MISSING_API));
    }

    @Test
    void missingProviderYieldsNoGateway() {
        assertNull(ReflectiveEconomyGateway.create(fakePlugin(), VAULT2_API, api -> null));
    }

    @Test
    void readsBalanceThroughReflection() {
        UUID playerId = UUID.randomUUID();
        ReflectiveEconomyGateway gateway = gatewayFor(provider(balances(playerId, "25.50")));

        assertEquals(0, new BigDecimal("25.50").compareTo(gateway.getBalance(playerId)));
        assertTrue(gateway.capabilities().isOperational());
        assertEquals("FakeEconomy", gateway.capabilities().providerName());
    }

    @Test
    void unknownAccountReadsAsZero() {
        ReflectiveEconomyGateway gateway = gatewayFor(provider(balances(UUID.randomUUID(), "10")));

        assertEquals(0, BigDecimal.ZERO.compareTo(gateway.getBalance(UUID.randomUUID())));
    }

    @Test
    void withdrawIsClampedToAvailableBalance() {
        UUID playerId = UUID.randomUUID();
        AtomicReference<BigDecimal> requested = new AtomicReference<>();
        ReflectiveEconomyGateway gateway = gatewayFor(provider(handler(balances(playerId, "25.50"), requested)));

        EconomyTransactionResult result = gateway.withdrawUpTo(playerId, new BigDecimal("100"), "income tax");

        // 只应扣到余额为止，而不是整笔失败。
        assertEquals(0, new BigDecimal("25.50").compareTo(requested.get()));
        assertTrue(result.success());
        assertEquals(0, new BigDecimal("25.50").compareTo(result.withdrawnAmount()));
    }

    @Test
    void withdrawWithZeroBalanceFailsWithoutCallingProvider() {
        UUID playerId = UUID.randomUUID();
        AtomicReference<BigDecimal> requested = new AtomicReference<>();
        ReflectiveEconomyGateway gateway = gatewayFor(provider(handler(balances(playerId, "0"), requested)));

        EconomyTransactionResult result = gateway.withdrawUpTo(playerId, new BigDecimal("10"), "wealth tax");

        assertFalse(result.success());
        assertNull(requested.get());
        assertNotNull(result.errorMessage());
    }

    @Test
    void knownAccountsComeFromProviderUuidNameMap() {
        UUID playerId = UUID.randomUUID();
        assertTrue(gatewayFor(provider(balances(playerId, "1"))).getKnownAccounts().contains(playerId));
    }

    @Test
    void formatFallsBackToPlainStringWhenProviderFormatReturnsNothing() {
        UUID playerId = UUID.randomUUID();
        ReflectiveEconomyGateway gateway = gatewayFor(provider(balances(playerId, "1")));

        // 假 provider 的 format 返回 null，网关应退回原始数值而不是显示 null。
        assertEquals("123.45", gateway.format(new BigDecimal("123.45")));
    }

    @Test
    void factoryFallsBackToNextApiWhenClassIsAbsent() {
        UUID playerId = UUID.randomUUID();
        Object provider = provider(balances(playerId, "25.50"));

        // 第一个候选类不存在（LinkageError 路径），第二个候选可用，应选中后者。
        EconomyGateway gateway = EconomyGatewayFactory.create(
            fakePlugin(),
            api -> provider,
            MISSING_API,
            VAULT2_API
        );

        assertNotNull(gateway);
        assertTrue(gateway instanceof ReflectiveEconomyGateway);
        assertEquals(0, new BigDecimal("25.50").compareTo(gateway.getBalance(playerId)));
    }

    @Test
    void factoryWithoutAnyApiDegradesGracefully() {
        EconomyGateway gateway = EconomyGatewayFactory.create(fakePlugin(), api -> null, MISSING_API);

        assertNotNull(gateway);
        assertFalse(gateway.capabilities().isOperational());
        assertEquals(0, BigDecimal.ZERO.compareTo(gateway.getBalance(UUID.randomUUID())));
        assertFalse(gateway.withdrawUpTo(UUID.randomUUID(), BigDecimal.TEN, "tax").success());
    }

    private static ReflectiveEconomyGateway gatewayFor(Object provider) {
        ReflectiveEconomyGateway gateway = ReflectiveEconomyGateway.create(fakePlugin(), VAULT2_API, api -> provider);
        assertNotNull(gateway, "gateway should resolve with a provided provider");
        return gateway;
    }

    private static Map<UUID, String> balances(UUID playerId, String amount) {
        return Map.of(playerId, amount);
    }

    /**
     * 用动态代理模拟 VaultUnlocked 的 Economy provider：只实现被测方法，
     * 其余方法返回默认值。
     */
    private static Object provider(Map<UUID, String> balances) {
        return provider(handler(balances, new AtomicReference<>()));
    }

    private static Object provider(java.lang.reflect.InvocationHandler handler) {
        return ReflectiveEconomyGateway.proxyOf(loadVault2(), handler);
    }

    private static java.lang.reflect.InvocationHandler handler(
        Map<UUID, String> balances,
        AtomicReference<BigDecimal> lastRequested
    ) {
        return (proxy, method, arguments) -> switch (method.getName()) {
            case "getName" -> "FakeEconomy";
            case "isEnabled" -> true;
            case "getBalance" -> {
                UUID accountId = (UUID) arguments[arguments.length - 1];
                yield new BigDecimal(balances.getOrDefault(accountId, "0"));
            }
            case "withdraw" -> {
                BigDecimal amount = (BigDecimal) arguments[arguments.length - 1];
                lastRequested.set(amount);
                yield economyResponse(true, amount, new BigDecimal("0"), null);
            }
            case "getUUIDNameMap" -> balances.keySet().stream()
                .collect(java.util.stream.Collectors.toMap(id -> id, id -> "player"));
            case "format" -> null;
            case "toString" -> "FakeEconomy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> null;
        };
    }

    /** 反射构造 VaultUnlocked 的 EconomyResponse（构造器签名为 金额、余额、状态、错误信息）。 */
    private static Object economyResponse(boolean success, BigDecimal amount, BigDecimal balance, String error) {
        Class<?> responseType = loadClass("net.milkbowl.vault2.economy.EconomyResponse");
        Class<?> responseEnum = loadClass("net.milkbowl.vault2.economy.EconomyResponse$ResponseType");
        Object successConstant = List.of(responseEnum.getEnumConstants()).stream()
            .filter(constant -> constant.toString().equals("SUCCESS"))
            .findFirst()
            .orElse(null);

        if (!success) {
            successConstant = List.of(responseEnum.getEnumConstants()).stream()
                .filter(constant -> constant.toString().equals("FAILURE"))
                .findFirst()
                .orElse(null);
        }

        try {
            Constructor<?> constructor = responseType.getConstructor(
                BigDecimal.class,
                BigDecimal.class,
                responseEnum,
                String.class
            );
            return constructor.newInstance(amount, balance, successConstant, error);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to build EconomyResponse test double", failure);
        }
    }

    private static Class<?> loadVault2() {
        return loadClass(VAULT2_API);
    }

    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("Test classpath is missing " + name, failure);
        }
    }

    private static Plugin fakePlugin() {
        return (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getLogger" -> java.util.logging.Logger.getLogger("YinwuTaxTest");
                case "getName" -> "YinwuTax";
                case "toString" -> "FakePlugin";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> null;
            }
        );
    }
}
