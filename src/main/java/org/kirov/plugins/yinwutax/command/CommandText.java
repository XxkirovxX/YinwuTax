package org.kirov.plugins.yinwutax.command;

import org.bukkit.ChatColor;

public final class CommandText {

    private static final String PREFIX = ChatColor.GOLD + "[YinwuTax] " + ChatColor.RESET;

    private CommandText() {
    }

    public static String prefixed(String message) {
        return PREFIX + message;
    }

    public static String usage(String usage) {
        return prefixed(ChatColor.YELLOW + "用法: " + ChatColor.WHITE + usage);
    }

    public static String success(String message) {
        return prefixed(ChatColor.GREEN + message);
    }

    public static String warning(String message) {
        return prefixed(ChatColor.YELLOW + message);
    }

    public static String error(String message) {
        return prefixed(ChatColor.RED + message);
    }

    public static String permission(String permission) {
        return error("你没有权限: " + ChatColor.WHITE + permission);
    }

    public static String statusHeader(String playerName) {
        return prefixed(ChatColor.GOLD + "" + ChatColor.BOLD + "税务状态"
            + ChatColor.GRAY + " - "
            + ChatColor.WHITE + playerName);
    }

    public static String statusLine(String label, Object value) {
        return prefixed(ChatColor.GRAY + label + ChatColor.AQUA + String.valueOf(value));
    }
}
