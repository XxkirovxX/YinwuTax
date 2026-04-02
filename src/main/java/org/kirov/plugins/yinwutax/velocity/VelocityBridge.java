package org.kirov.plugins.yinwutax.velocity;

import org.bukkit.plugin.java.JavaPlugin;

public class VelocityBridge {

    private final JavaPlugin plugin;
    private final boolean enabled;

    public VelocityBridge(JavaPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
    }

    public void start() {
        if (enabled) {
            plugin.getLogger().info("Velocity bridge is enabled in reserved mode.");
        }
    }

    public void stop() {
    }

    public boolean isEnabled() {
        return enabled;
    }
}
