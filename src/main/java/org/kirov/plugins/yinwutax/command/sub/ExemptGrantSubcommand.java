package org.kirov.plugins.yinwutax.command.sub;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.kirov.plugins.yinwutax.YinwuTaxPlugin;

public class ExemptGrantSubcommand {

    private final YinwuTaxPlugin plugin;

    public ExemptGrantSubcommand(YinwuTaxPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return false;
        }

        if (!plugin.getServices().getConfigModel().exemption().enabled()) {
            sender.sendMessage("Exemption feature is disabled in config.");
            return true;
        }

        int count;
        try {
            count = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("Invalid exemption count: " + args[2]);
            return true;
        }

        if (count <= 0) {
            sender.sendMessage("Exemption count must be positive.");
            return true;
        }

        int maxGrant = plugin.getServices().getConfigModel().exemption().maxGrantPerCommand();
        if ("grant".equalsIgnoreCase(args[0]) && count > maxGrant) {
            sender.sendMessage("Grant count exceeds configured max: " + maxGrant);
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        UUID targetId = target.getUniqueId();

        if ("grant".equalsIgnoreCase(args[0])) {
            plugin.getServices().grantExemptions(targetId, count);
            sender.sendMessage("Granted " + count + " exemption(s) to " + target.getName() + ".");
            return true;
        }

        if ("take".equalsIgnoreCase(args[0])) {
            plugin.getServices().takeExemptions(targetId, count);
            sender.sendMessage("Removed " + count + " exemption(s) from " + target.getName() + ".");
            return true;
        }

        return false;
    }
}
