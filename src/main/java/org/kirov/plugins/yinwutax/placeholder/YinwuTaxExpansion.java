package org.kirov.plugins.yinwutax.placeholder;

import java.util.UUID;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kirov.plugins.yinwutax.YinwuTaxPlugin;

public class YinwuTaxExpansion extends PlaceholderExpansion {

    private final YinwuTaxPlugin plugin;

    public YinwuTaxExpansion(YinwuTaxPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yinwutax";
    }

    @Override
    public @NotNull String getAuthor() {
        return "kirov";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        UUID playerId = player.getUniqueId();
        return switch (params.toLowerCase()) {
            case "income_rate" -> plugin.getServices().getCurrentIncomeRate(playerId).toPlainString();
            case "wealth_rate" -> plugin.getServices().getCurrentWealthRate(playerId).toPlainString();
            case "exemptions" -> Integer.toString(plugin.getServices().getRemainingExemptions(playerId));
            case "last_tax_amount" -> plugin.getServices().getLastTaxAmount(playerId).toPlainString();
            case "linked_accounts" -> Integer.toString(plugin.getServices().getLinkedAccountCount(playerId));
            default -> "";
        };
    }
}
