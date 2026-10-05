package org.kirov.plugins.yinwutax.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.kirov.plugins.yinwutax.storage.TaxDataSnapshot;
import org.kirov.plugins.yinwutax.tax.exemption.ExemptionService;
import org.kirov.plugins.yinwutax.tax.exemption.ExemptionStore;
import org.kirov.plugins.yinwutax.tax.headcount.HeadcountOverrideStore;
import org.kirov.plugins.yinwutax.tax.headcount.HeadcountTaxService;
import org.kirov.plugins.yinwutax.tax.headcount.IpHistoryTracker;
import org.kirov.plugins.yinwutax.tax.income.IncomeBracketResolver;

/**
 * 税收提醒测试。
 *
 * <p>重点覆盖三件事：
 * <ul>
 *   <li>逐笔提醒里的估算税额是否按「基础档位 × 人头税附加」算对；</li>
 *   <li>免税状态下是否给出"可整单免除"的提示；</li>
 *   <li>离线玩家是否不收到逐笔刷屏、而是在上线时拿到一条汇总。</li>
 * </ul>
 */
class TaxNotifierTest {

    private static final BigDecimal BASE_RATE = new BigDecimal("0.03");
    private static final BigDecimal FINAL_RATE = new BigDecimal("0.036");
    private static final BigDecimal EXTRA_RATE = new BigDecimal("0.20");

    @Test
    void incomeNoticeWithoutExemptionShowsEstimatedTaxAndFormula() {
        UUID playerId = UUID.randomUUID();
        Fixture fixture = new Fixture(playerId, 1, false);

        fixture.notifier.notifyIncome(playerId, BigDecimal.ONE);

        assertEquals(2, fixture.messages.size(), "应给出提醒行与计算方式行");
        assertTrue(fixture.messages.get(0).contains("估算税额"), fixture.messages.get(0));
        // 1 × 0.03 = 0.03（人头税倍率在期末结算时才对整周期收入生效，逐笔估算不含它）
        assertTrue(fixture.messages.get(0).contains("0.03"), fixture.messages.get(0));
        assertTrue(fixture.messages.get(1).contains("基础税率 0.03"), fixture.messages.get(1));
    }

    @Test
    void incomeNoticeWithExemptionWarnsWholeBillIsWaived() {
        UUID playerId = UUID.randomUUID();
        Fixture fixture = new Fixture(playerId, 1, true);
        fixture.exemptionService.grant(playerId, 2);

        fixture.notifier.notifyIncome(playerId, BigDecimal.ONE);

        assertEquals(3, fixture.messages.size(), "免税状态下应多一条提示");
        assertTrue(fixture.messages.get(2).contains("整单免除"), fixture.messages.get(2));
        assertTrue(fixture.messages.get(2).contains("2"), fixture.messages.get(2));
    }

    @Test
    void perTransactionEstimateIncludesHeadcountMultiplier() {
        UUID playerId = UUID.randomUUID();
        Fixture fixture = new Fixture(playerId, 3, true);

        fixture.notifier.notifyIncome(playerId, BigDecimal.ONE);

        // 额外账户 = 3-1 = 2 → 1 × (0.03 × 1.4) = 0.042 → 展示保留两位 = 0.04
        assertTrue(fixture.messages.get(0).contains("0.04"), fixture.messages.get(0));
        assertTrue(fixture.messages.get(1).contains("额外账户 2 个"), fixture.messages.get(1));
    }

    @Test
    void offlinePlayerReceivesNoPerTransactionSpamButGetsSummaryOnJoin() {
        UUID playerId = UUID.randomUUID();
        Fixture fixture = new Fixture(playerId, 1, false);
        fixture.playerOnline = false;

        fixture.notifier.notifyIncome(playerId, BigDecimal.ONE);
        assertTrue(fixture.messages.isEmpty(), "离线玩家不应收到逐笔提醒");

        fixture.notifier.notifySettlement(
            TaxNotifier.INCOME_TAX_NAME,
            playerId,
            new BigDecimal("100.00"),
            FINAL_RATE,
            new BigDecimal("3.60"),
            new BigDecimal("3.60"),
            false,
            null
        );
        fixture.notifier.notifySettlement(
            TaxNotifier.WEALTH_TAX_NAME,
            playerId,
            new BigDecimal("1000.00"),
            new BigDecimal("0.05"),
            new BigDecimal("50.00"),
            new BigDecimal("50.00"),
            false,
            null
        );
        assertTrue(fixture.messages.isEmpty(), "离线时不应尝试发送");

        fixture.playerOnline = true;
        fixture.notifier.deliverOfflineSummary(fixture.player);

        assertEquals(1, fixture.messages.size(), "上线时应补发一条汇总");
        assertTrue(fixture.messages.get(0).contains("2"), fixture.messages.get(0));
        assertTrue(fixture.messages.get(0).contains("笔扣税"), fixture.messages.get(0));
        assertTrue(fixture.messages.get(0).contains("53.60"), fixture.messages.get(0));
    }

    @Test
    void onlineSettlementReportsExemptionAndFailureDistinctly() {
        UUID playerId = UUID.randomUUID();
        Fixture fixture = new Fixture(playerId, 1, false);

        fixture.notifier.notifySettlement(
            TaxNotifier.INCOME_TAX_NAME,
            playerId,
            new BigDecimal("100.00"),
            FINAL_RATE,
            new BigDecimal("3.60"),
            BigDecimal.ZERO,
            true,
            null
        );
        assertTrue(fixture.messages.get(0).contains("已免除"), fixture.messages.get(0));
        assertTrue(fixture.messages.get(0).contains("3.60"), fixture.messages.get(0));

        fixture.notifier.notifySettlement(
            TaxNotifier.WEALTH_TAX_NAME,
            playerId,
            new BigDecimal("1000.00"),
            new BigDecimal("0.05"),
            new BigDecimal("50.00"),
            BigDecimal.ZERO,
            false,
            "No balance available for tax collection."
        );
        assertTrue(fixture.messages.get(1).contains("扣款未成功"), fixture.messages.get(1));
    }

    @Test
    void failedCollectionDoesNotCountIntoOfflineSummary() {
        UUID playerId = UUID.randomUUID();
        Fixture fixture = new Fixture(playerId, 1, false);
        fixture.playerOnline = false;

        fixture.notifier.notifySettlement(
            TaxNotifier.INCOME_TAX_NAME,
            playerId,
            new BigDecimal("100.00"),
            FINAL_RATE,
            new BigDecimal("3.60"),
            BigDecimal.ZERO,
            false,
            "boom"
        );

        fixture.playerOnline = true;
        fixture.notifier.deliverOfflineSummary(fixture.player);

        assertTrue(fixture.messages.isEmpty(), "没有真的扣到钱就不该提示被扣税");
    }

    /** 组装一套可用真实对象驱动的测试环境。 */
    private static final class Fixture {

        private final UUID playerId;
        private final TaxDataSnapshot snapshot = new TaxDataSnapshot();
        private final ExemptionService exemptionService;
        private final TaxNotifier notifier;
        private final List<String> messages = new ArrayList<>();
        private final Player player;
        private boolean playerOnline = true;

        private Fixture(UUID playerId, int linkedAccounts, boolean headcountEnabled) {
            this.playerId = playerId;

            IpHistoryTracker tracker = new IpHistoryTracker(snapshot, "test-salt");
            tracker.record(playerId, "shared-ip-hash");
            for (int index = 1; index < linkedAccounts; index++) {
                tracker.record(UUID.randomUUID(), "shared-ip-hash");
            }

            this.exemptionService = new ExemptionService(new ExemptionStore(snapshot.getExemptions()));
            this.player = fakePlayer(playerId, messages);
            Server server = fakeServer(playerId, player, () -> playerOnline);

            this.notifier = new TaxNotifier(
                fakePlugin(server),
                new IncomeBracketResolver(List.of(
                    new org.kirov.plugins.yinwutax.config.TaxBracket(BigDecimal.ZERO, null, BASE_RATE)
                )),
                new HeadcountTaxService(new HeadcountOverrideStore(snapshot.getHeadcountOverrides()), EXTRA_RATE),
                tracker,
                exemptionService,
                headcountEnabled,
                true
            );
        }
    }

    private static Player fakePlayer(UUID playerId, List<String> messages) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "sendMessage" -> {
                    if (arguments.length == 1 && arguments[0] instanceof String text) {
                        messages.add(text);
                    }
                    yield null;
                }
                case "getUniqueId" -> playerId;
                case "getName" -> "Tester";
                case "isOnline" -> true;
                case "toString" -> "FakePlayer";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> null;
            }
        );
    }

    private static Server fakeServer(UUID playerId, Player player, java.util.function.BooleanSupplier online) {
        return (Server) Proxy.newProxyInstance(
            Server.class.getClassLoader(),
            new Class<?>[] {Server.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getPlayer" -> {
                    if (arguments.length == 1 && playerId.equals(arguments[0]) && online.getAsBoolean()) {
                        yield player;
                    }
                    yield null;
                }
                case "getPlayerExact" -> online.getAsBoolean() ? player : null;
                case "getLogger" -> java.util.logging.Logger.getLogger("YinwuTaxTest");
                case "toString" -> "FakeServer";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> null;
            }
        );
    }

    private static Plugin fakePlugin(Server server) {
        return (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getServer" -> server;
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
