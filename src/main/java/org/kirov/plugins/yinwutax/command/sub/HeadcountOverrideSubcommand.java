package org.kirov.plugins.yinwutax.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.kirov.plugins.yinwutax.YinwuTaxPlugin;
import org.kirov.plugins.yinwutax.command.CommandText;

public class HeadcountOverrideSubcommand {

    private final YinwuTaxPlugin plugin;

    public HeadcountOverrideSubcommand(YinwuTaxPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(CommandText.usage("/yinwutax headcount <set|clear> <玩家> [额外账户数]"));
            return true;
        }

        if (!plugin.getServices().getConfigModel().headcountTax().enabled()) {
            sender.sendMessage(CommandText.warning("人头税功能已在配置中关闭。"));
            return true;
        }

        if ("set".equalsIgnoreCase(args[0]) && args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            int extraAccounts;
            try {
                extraAccounts = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(CommandText.error("额外账户数格式无效: " + args[2]));
                return true;
            }
            if (extraAccounts < 0) {
                sender.sendMessage(CommandText.error("额外账户数不能为负数。"));
                return true;
            }
            plugin.getServices().setHeadcountOverride(target.getUniqueId(), extraAccounts);
            sender.sendMessage(CommandText.success("已将 " + resolvePlayerName(target) + " 的额外账户数设置为 " + extraAccounts + "。"));
            return true;
        }

        if ("clear".equalsIgnoreCase(args[0])) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            plugin.getServices().clearHeadcountOverride(target.getUniqueId());
            sender.sendMessage(CommandText.success("已清除 " + resolvePlayerName(target) + " 的人头税覆盖设置。"));
            return true;
        }

        sender.sendMessage(CommandText.usage("/yinwutax headcount <set|clear> <玩家> [额外账户数]"));
        return true;
    }

    private String resolvePlayerName(OfflinePlayer player) {
        if (player.getName() == null || player.getName().isBlank()) {
            return player.getUniqueId().toString();
        }
        return player.getName();
    }
}
