package org.kirov.plugins.yinwutax.text;

import java.math.BigDecimal;

import org.bukkit.ChatColor;

/**
 * 面向玩家的税收提醒文案。
 *
 * <p>格式刻意与 {@code CommandText} 保持一致：同样的 {@code [YinwuTax]} 前缀、
 * 金色标签 + 灰色说明 + 青色数值。
 */
public final class TaxText {

    private static final String PREFIX = ChatColor.GOLD + "[YinwuTax] " + ChatColor.RESET;
    private static final String LABEL_INCOME = ChatColor.GOLD + "所得税" + ChatColor.RESET;
    private static final String LABEL_WEALTH = ChatColor.GOLD + "财富税" + ChatColor.RESET;
    private static final String AMOUNT = ChatColor.AQUA.toString();

    private TaxText() {
    }

    /**
     * 逐笔收入提醒：交易发生时给出估算税额。
     *
     * @param exempt 当前是否还有免税次数，若有则该笔在期末会被整单免除
     */
    public static String incomeNotice(BigDecimal transactionAmount, BigDecimal estimatedTax, boolean exempt) {
        return PREFIX
            + LABEL_INCOME
            + ChatColor.GRAY + " 本笔 +" + AMOUNT + plain(transactionAmount)
            + ChatColor.GRAY + "，估算税额 " + AMOUNT + plain(estimatedTax)
            + ChatColor.DARK_GRAY + "（期末按应税总收入结算）";
    }

    /** 估算公式的人话说明，让玩家能看懂税额是怎么来的。 */
    public static String incomeFormula(BigDecimal transactionAmount, BigDecimal baseRate, BigDecimal finalRate, int extraAccounts) {
        return PREFIX
            + ChatColor.DARK_GRAY + "计算方式: 本笔 " + plain(transactionAmount)
            + " × 基础税率 " + plain(baseRate)
            + headcountSuffix(extraAccounts)
            + " = " + plain(finalRate);
    }

    /** 有免税次数可用时的提示。 */
    public static String exemptionHint(int remaining) {
        return PREFIX
            + ChatColor.GREEN + "本笔已可使用免税，期末结算时整单免除"
            + ChatColor.GRAY + "（剩余免税次数 " + ChatColor.AQUA + remaining + ChatColor.GRAY + "）";
    }

    /** 财富税等期末结算的扣税提醒。 */
    public static String collected(String taxName, BigDecimal amount, BigDecimal taxableBase, BigDecimal rate) {
        return taxName(taxName)
            + ChatColor.GRAY + " 已扣除 " + ChatColor.RED + plain(amount)
            + ChatColor.GRAY + "，计税基础 " + AMOUNT + plain(taxableBase)
            + ChatColor.GRAY + " × 税率 " + AMOUNT + plain(rate);
    }

    /** 结算时被免税整单免除。 */
    public static String exempted(String taxName, BigDecimal waivedAmount) {
        return taxName(taxName)
            + ChatColor.GREEN + " 本张税单已免除"
            + ChatColor.GRAY + "，免除金额 " + AMOUNT + plain(waivedAmount);
    }

    /** 结算时扣款失败（例如余额不足）。 */
    public static String failed(String taxName, BigDecimal attemptedAmount, String reason) {
        return taxName(taxName)
            + ChatColor.YELLOW + " 扣款未成功"
            + ChatColor.GRAY + "，应交 " + AMOUNT + plain(attemptedAmount)
            + ChatColor.GRAY + "，原因: " + ChatColor.WHITE + describe(reason);
    }

    /** 离线期间的汇总，玩家上线时补发。 */
    public static String offlineSummary(int charges, BigDecimal total) {
        return PREFIX
            + ChatColor.GOLD + "离线期间共产生 " + ChatColor.AQUA + charges + ChatColor.GOLD + " 笔扣税"
            + ChatColor.GRAY + "，合计 " + ChatColor.RED + plain(total)
            + ChatColor.GRAY + "。使用 " + ChatColor.WHITE + "/yinwutax status" + ChatColor.GRAY + " 查看明细。";
    }

    private static String taxName(String taxName) {
        return PREFIX + ChatColor.GOLD + taxName + ChatColor.RESET;
    }

    private static String headcountSuffix(int extraAccounts) {
        if (extraAccounts <= 0) {
            return "";
        }

        return ChatColor.DARK_GRAY + " × 人头税附加（额外账户 " + extraAccounts + " 个）";
    }

    private static String describe(String reason) {
        return reason == null || reason.isBlank() ? "余额不足或被经济插件拒绝" : reason;
    }

    /** 统一用两位小数展示金额，避免出现 0.0 这类不一致的写法。 */
    private static String plain(BigDecimal amount) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
