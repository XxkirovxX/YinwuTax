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
        sender.sendMessage("YinwuTax configuration reloaded.");
        return true;
    }

    private boolean handleSettle(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return false;
        }

        if ("income".equalsIgnoreCase(args[0])) {
            int settled = plugin.getServices().runIncomeSettlement();
            sender.sendMessage("Income settlement complete: " + settled + " statement(s).");
            return true;
        }

        if ("wealth".equalsIgnoreCase(args[0])) {
            int settled = plugin.getServices().runWealthSettlement();
            sender.sendMessage("Wealth settlement complete: " + settled + " statement(s).");
            return true;
        }

        return false;
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

        sender.sendMessage("You do not have permission: " + permission);
        return false;
    }
}
