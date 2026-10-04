package org.kirov.plugins.yinwutax.command.sub;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.kirov.plugins.yinwutax.YinwuTaxPlugin;
import org.kirov.plugins.yinwutax.command.CommandText;

public class ExemptGrantSubcommand {

    private final YinwuTaxPlugin plugin;

    public ExemptGrantSubcommand(YinwuTaxPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(CommandText.usage("/yinwutax exempt <grant|take> <玩家> <数量>"));
            return true;
        }

        if (!plugin.getServices().getConfigModel().exemption().enabled()) {
            sender.sendMessage(CommandText.warning("免税功能已在配置中关闭。"));
            return true;
        }

        int count;
        try {
            count = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(CommandText.error("免税次数格式无效: " + args[2]));
            return true;
        }

        if (count <= 0) {
            sender.sendMessage(CommandText.error("免税次数必须大于 0。"));
            return true;
        }

        int maxGrant = plugin.getServices().getConfigModel().exemption().maxGrantPerCommand();
        if ("grant".equalsIgnoreCase(args[0]) && count > maxGrant) {
            sender.sendMessage(CommandText.error("发放数量超过配置上限: " + maxGrant));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetId = target.getUniqueId();

        if ("grant".equalsIgnoreCase(args[0])) {
            plugin.getServices().grantExemptions(targetId, count);
            sender.sendMessage(CommandText.success("已向 " + resolvePlayerName(target) + " 发放 " + count + " 次免税。"));
            return true;
        }

        if ("take".equalsIgnoreCase(args[0])) {
            plugin.getServices().takeExemptions(targetId, count);
            sender.sendMessage(CommandText.success("已从 " + resolvePlayerName(target) + " 扣除 " + count + " 次免税。"));
            return true;
        }

        sender.sendMessage(CommandText.usage("/yinwutax exempt <grant|take> <玩家> <数量>"));
        return true;
    }

    private String resolvePlayerName(OfflinePlayer player) {
        if (player.getName() == null || player.getName().isBlank()) {
            return player.getUniqueId().toString();
        }
        return player.getName();
    }
}
