package org.kirov.plugins.yinwutax.command;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kirov.plugins.yinwutax.YinwuTaxPlugin;

public class YinwuTaxCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = Arrays.asList("status", "reload", "exempt", "headcount", "settle");

    private final YinwuTaxPlugin plugin;
    private final YinwuTaxAdminCommand adminCommand;

    public YinwuTaxCommand(YinwuTaxPlugin plugin, YinwuTaxAdminCommand adminCommand) {
        this.plugin = plugin;
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            // 管理子命令先尝试在这里分流，避免后面掉回默认的 status 展示逻辑。
            boolean handled = adminCommand.handle(sender, args);
            if (handled) {
                return true;
            }

            if (ROOT.contains(args[0].toLowerCase()) && !"status".equalsIgnoreCase(args[0])) {
                return true;
            }
        }

        if (!(sender instanceof Player player)) {
            if (args.length > 1 && "status".equalsIgnoreCase(args[0]) && sender.hasPermission("yinwutax.command.status")) {
                return sendStatus(sender, Bukkit.getOfflinePlayer(args[1]));
            }
            sender.sendMessage(CommandText.usage("/yinwutax status <玩家>"));
            return true;
        }

        OfflinePlayer target = args.length > 1 && "status".equalsIgnoreCase(args[0])
            ? Bukkit.getOfflinePlayer(args[1])
            : player;

        if (!target.getUniqueId().equals(player.getUniqueId()) && !sender.hasPermission("yinwutax.command.status") && !sender.hasPermission("yinwutax.admin")) {
            sender.sendMessage(CommandText.permission("yinwutax.command.status"));
            return true;
        }

        return sendStatus(sender, target);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return ROOT.stream().filter(option -> option.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }

    private boolean sendStatus(CommandSender sender, OfflinePlayer target) {
        UUID targetId = target.getUniqueId();
        Set<UUID> linkedAccounts = plugin.getServices().getLinkedAccounts(targetId);
        String targetName = resolvePlayerName(target);
        String linkedAccountDisplay = LinkedAccountDisplayFormatter.format(
            linkedAccounts,
            linkedPlayerId -> resolvePlayerName(Bukkit.getOfflinePlayer(linkedPlayerId))
        );

        // 这里聚合展示当前税务状态，避免命令主流程里堆太多重复发送逻辑。
        sender.sendMessage(CommandText.statusHeader(targetName));
        sender.sendMessage(CommandText.statusLine("所得税率: ", safeEconomyLookup(targetId, plugin.getServices()::getCurrentIncomeRate)));
        sender.sendMessage(CommandText.statusLine("财产税率: ", safeEconomyLookup(targetId, plugin.getServices()::getCurrentWealthRate)));
        sender.sendMessage(CommandText.statusLine("剩余免税次数: ", plugin.getServices().getRemainingExemptions(targetId)));
        sender.sendMessage(CommandText.statusLine("上次税额: ", plugin.getServices().getLastTaxAmount(targetId)));
        sender.sendMessage(CommandText.statusLine("关联账户数: ", linkedAccounts.size()));
        sender.sendMessage(CommandText.statusLine("关联账户列表: ", linkedAccountDisplay));
        return true;
    }

    /**
     * 读取依赖经济插件的数值。
     *
     * <p>这里捕获 {@link Throwable} 而不是 {@link Exception}：缺少经济插件时抛的是
     * {@link NoClassDefFoundError}（属于 {@link Error}），若放任它冒出去，
     * 命令会被服务端记成 "Command exception" 并把堆栈甩给玩家。
     */
    private String safeEconomyLookup(UUID playerId, Function<UUID, BigDecimal> lookup) {
        try {
            return lookup.apply(playerId).toPlainString();
        } catch (Throwable failure) {
            plugin.getLogger().log(
                Level.WARNING,
                "Failed to read economy-dependent value for " + playerId + "; showing 0 instead.",
                failure
            );
            return "0";
        }
    }

    private String resolvePlayerName(OfflinePlayer player) {
        if (player.getName() == null || player.getName().isBlank()) {
            return player.getUniqueId().toString();
        }
        return player.getName();
    }
}
