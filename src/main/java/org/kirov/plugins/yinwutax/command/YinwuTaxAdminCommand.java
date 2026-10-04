package org.kirov.plugins.yinwutax.command;

import org.bukkit.command.CommandSender;
import org.kirov.plugins.yinwutax.YinwuTaxPlugin;
import org.kirov.plugins.yinwutax.command.sub.ExemptGrantSubcommand;
import org.kirov.plugins.yinwutax.command.sub.HeadcountOverrideSubcommand;

public class YinwuTaxAdminCommand {

    private final YinwuTaxPlugin plugin;
    private final ExemptGrantSubcommand exemptGrantSubcommand;
    private final HeadcountOverrideSubcommand headcountOverrideSubcommand;

    public YinwuTaxAdminCommand(YinwuTaxPlugin plugin) {
        this.plugin = plugin;
        this.exemptGrantSubcommand = new ExemptGrantSubcommand(plugin);
        this.headcountOverrideSubcommand = new HeadcountOverrideSubcommand(plugin);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return false;
        }

        // 这里只负责一层根命令分发，具体参数校验交给下一级子命令处理。
        return switch (args[0].toLowerCase()) {
            case "reload" -> hasPermission(sender, "yinwutax.command.reload") && handleReload(sender);
            case "exempt" -> hasPermission(sender, "yinwutax.command.exempt") && exemptGrantSubcommand.handle(sender, slice(args));
            case "headcount" -> hasPermission(sender, "yinwutax.command.headcount") && headcountOverrideSubcommand.handle(sender, slice(args));
            case "settle" -> hasPermission(sender, "yinwutax.command.settle") && handleSettle(sender, slice(args));
            default -> false;
        };
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reloadServices();
        sender.sendMessage(CommandText.success("配置已重载。"));
        return true;
    }

    private boolean handleSettle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(CommandText.usage("/yinwutax settle <income|wealth>"));
            return true;
        }

        if ("income".equalsIgnoreCase(args[0])) {
            int settled = plugin.getServices().runIncomeSettlement();
            sender.sendMessage(CommandText.success("所得税结算完成，共处理 " + settled + " 条记录。"));
            return true;
        }

        if ("wealth".equalsIgnoreCase(args[0])) {
            int settled = plugin.getServices().runWealthSettlement();
            sender.sendMessage(CommandText.success("财产税结算完成，共处理 " + settled + " 条记录。"));
            return true;
        }

        sender.sendMessage(CommandText.usage("/yinwutax settle <income|wealth>"));
        return true;
    }

    private String[] slice(String[] args) {
        String[] sliced = new String[Math.max(0, args.length - 1)];
        System.arraycopy(args, 1, sliced, 0, sliced.length);
        return sliced;
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        if (sender.hasPermission("yinwutax.admin") || sender.hasPermission(permission)) {
            return true;
        }

        sender.sendMessage(CommandText.permission(permission));
        return false;
    }
}
