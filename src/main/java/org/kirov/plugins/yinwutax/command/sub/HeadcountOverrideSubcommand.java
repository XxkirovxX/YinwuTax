package org.kirov.plugins.yinwutax.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.kirov.plugins.yinwutax.YinwuTaxPlugin;

public class HeadcountOverrideSubcommand {

    private final YinwuTaxPlugin plugin;

    public HeadcountOverrideSubcommand(YinwuTaxPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return false;
        }

        if (!plugin.getServices().getConfigModel().headcountTax().enabled()) {
            sender.sendMessage("Headcount tax feature is disabled in config.");
            return true;
        }

        if ("set".equalsIgnoreCase(args[0]) && args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            int extraAccounts;
            try {
                extraAccounts = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("Invalid extra account count: " + args[2]);
                return true;
            }
            if (extraAccounts < 0) {
                sender.sendMessage("Extra account count cannot be negative.");
                return true;
            }
            plugin.getServices().setHeadcountOverride(target.getUniqueId(), extraAccounts);
            sender.sendMessage("Headcount override set for " + target.getName() + ": " + extraAccounts);
            return true;
        }

        if ("clear".equalsIgnoreCase(args[0])) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            plugin.getServices().clearHeadcountOverride(target.getUniqueId());
            sender.sendMessage("Headcount override cleared for " + target.getName() + ".");
            return true;
        }

        return false;
    }
}
