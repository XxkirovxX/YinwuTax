package org.kirov.plugins.yinwutax;

import java.io.File;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.kirov.plugins.yinwutax.bootstrap.ServiceRegistry;
import org.kirov.plugins.yinwutax.command.YinwuTaxAdminCommand;
import org.kirov.plugins.yinwutax.command.YinwuTaxCommand;
import org.kirov.plugins.yinwutax.placeholder.YinwuTaxExpansion;

public class YinwuTaxPlugin extends JavaPlugin {

    private ServiceRegistry services;
    private YinwuTaxExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledResource("messages.yml");
        saveBundledResource("storage.yml");
        reloadServices();
        registerCommands();
        registerPlaceholders();
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }

        if (services != null) {
            services.close();
            services = null;
        }
    }

    public ServiceRegistry getServices() {
        return services;
    }

    public void reloadServices() {
        if (services != null) {
            services.close();
        }

        reloadConfig();
        services = ServiceRegistry.bootstrap(this);
        services.start();
    }

    private void registerCommands() {
        PluginCommand command = getCommand("yinwutax");
        if (command == null) {
            getLogger().warning("Command yinwutax is missing from plugin.yml.");
            return;
        }

        YinwuTaxAdminCommand adminCommand = new YinwuTaxAdminCommand(this);
        YinwuTaxCommand commandExecutor = new YinwuTaxCommand(this, adminCommand);
        command.setExecutor(commandExecutor);
        command.setTabCompleter(commandExecutor);
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }

        placeholderExpansion = new YinwuTaxExpansion(this);
        placeholderExpansion.register();
    }

    private void saveBundledResource(String resourcePath) {
        File target = new File(getDataFolder(), resourcePath);
        if (!target.exists()) {
            saveResource(resourcePath, false);
        }
    }
}
