package org.kirov.plugins.yinwutax.notify;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.kirov.plugins.yinwutax.tax.exemption.ExemptionService;
import org.kirov.plugins.yinwutax.tax.headcount.HeadcountTaxService;
import org.kirov.plugins.yinwutax.tax.headcount.IpHistoryTracker;
import org.kirov.plugins.yinwutax.tax.income.IncomeBracketResolver;
import org.kirov.plugins.yinwutax.text.TaxText;

/**
 * 税收提醒的发送方。
 *
 * <p>两类提醒：
 * <ul>
 *   <li><b>逐笔收入提醒</b>：收到收入时即时告知本笔的估算税额。所得税是期末按周期总收入结算的，
 *       因此这里只是估算，文案里必须如实标注，避免玩家把它当成已扣款。</li>
 *   <li><b>结算提醒</b>：真正扣款（或被免税免除、或扣款失败）时告知实际结果。</li>
 * </ul>
 *
 * <p>扣税时不在线的玩家，其结算提醒会累积成一条汇总，在上线时补发。
 */
public class TaxNotifier implements Listener {

    /** 税种展示名。 */
    public static final String INCOME_TAX_NAME = "所得税";
    public static final String WEALTH_TAX_NAME = "财富税";

    private final Plugin plugin;
    private final IncomeBracketResolver incomeBracketResolver;
    private final HeadcountTaxService headcountTaxService;
    private final IpHistoryTracker ipHistoryTracker;
    private final ExemptionService exemptionService;
    private final boolean headcountEnabled;
    private final boolean exemptionEnabled;

    /** 离线期间的扣税汇总。 */
    private final Map<UUID, OfflineSummary> offlineSummaries = new ConcurrentHashMap<>();

    public TaxNotifier(
        Plugin plugin,
        IncomeBracketResolver incomeBracketResolver,
        HeadcountTaxService headcountTaxService,
        IpHistoryTracker ipHistoryTracker,
        ExemptionService exemptionService,
        boolean headcountEnabled,
        boolean exemptionEnabled
    ) {
        this.plugin = plugin;
        this.incomeBracketResolver = incomeBracketResolver;
        this.headcountTaxService = headcountTaxService;
        this.ipHistoryTracker = ipHistoryTracker;
        this.exemptionService = exemptionService;
        this.headcountEnabled = headcountEnabled;
        this.exemptionEnabled = exemptionEnabled;
    }

    /**
     * 逐笔收入提醒。
     *
     * <p>估算口径是「本笔金额单独落入的档位」，这是玩家最容易理解的方式；
     * 期末实际税率取决于周期总收入，可能落在更高档位，因此文案标注为估算。
     */
    public void notifyIncome(UUID playerId, BigDecimal transactionAmount) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            // 离线玩家只在期末结算时收到汇总，逐笔提醒不堆积。
            return;
        }

        BigDecimal baseRate = incomeBracketResolver.resolveRate(transactionAmount);
        BigDecimal finalRate = applyHeadcount(playerId, baseRate);
        BigDecimal estimatedTax = transactionAmount.multiply(finalRate).setScale(2, RoundingMode.HALF_UP);
        boolean exempt = exemptionEnabled && exemptionService.remaining(playerId) > 0;

        player.sendMessage(TaxText.incomeNotice(transactionAmount, estimatedTax, exempt));
        player.sendMessage(TaxText.incomeFormula(transactionAmount, baseRate, finalRate, extraAccounts(playerId)));

        if (exempt) {
            player.sendMessage(TaxText.exemptionHint(exemptionService.remaining(playerId)));
        }
    }

    /**
     * 期末结算的扣税提醒：扣款成功、被免税免除、扣款失败三种结果都会告知。
     *
     * @param taxName 税种名称
     * @param playerId 纳税人
     * @param taxableBase 计税基础（应税总收入或余额）
     * @param rate 税率
     * @param dueAmount 应交税额
     * @param collectedAmount 实际扣除金额
     * @param exempted 是否被免税整单免除
     * @param failureReason 扣款失败原因，成功时为 {@code null}
     */
    public void notifySettlement(
        String taxName,
        UUID playerId,
        BigDecimal taxableBase,
        BigDecimal rate,
        BigDecimal dueAmount,
        BigDecimal collectedAmount,
        boolean exempted,
        String failureReason
    ) {
        String message;
        if (exempted) {
            message = TaxText.exempted(taxName, dueAmount);
        } else if (failureReason != null || collectedAmount.signum() <= 0) {
            message = TaxText.failed(taxName, dueAmount, failureReason);
        } else {
            message = TaxText.collected(taxName, collectedAmount, taxableBase, rate);
        }

        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(message);
            return;
        }

        if (collectedAmount.signum() > 0) {
            // 只有真的扣到钱才计入离线汇总，避免"扣款失败也通知被扣了"。
            offlineSummaries.computeIfAbsent(playerId, ignored -> new OfflineSummary()).accumulate(collectedAmount);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        deliverOfflineSummary(event.getPlayer());
    }

    /** 玩家上线时补发离线期间的扣税汇总。 */
    public void deliverOfflineSummary(Player player) {
        if (player == null) {
            return;
        }

        OfflineSummary summary = offlineSummaries.remove(player.getUniqueId());
        if (summary == null) {
            return;
        }

        player.sendMessage(TaxText.offlineSummary(summary.count(), summary.total()));
    }

    private BigDecimal applyHeadcount(UUID playerId, BigDecimal baseRate) {
        if (!headcountEnabled) {
            return baseRate;
        }

        return headcountTaxService.resolveFinalIncomeTaxRate(
            playerId,
            baseRate,
            ipHistoryTracker.getLinkedAccountCount(playerId)
        );
    }

    private int extraAccounts(UUID playerId) {
        if (!headcountEnabled) {
            return 0;
        }

        return Math.max(0, ipHistoryTracker.getLinkedAccountCount(playerId) - 1);
    }

    /** 离线扣税汇总：笔数与合计金额。 */
    private static final class OfflineSummary {

        private int count;
        private BigDecimal total = BigDecimal.ZERO;

        private void accumulate(BigDecimal amount) {
            count++;
            total = total.add(amount);
        }

        private int count() {
            return count;
        }

        private BigDecimal total() {
            return total;
        }
    }
}
