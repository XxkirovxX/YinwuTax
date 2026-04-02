package org.kirov.plugins.yinwutax.storage.yaml;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.kirov.plugins.yinwutax.storage.StorageFacade;
import org.kirov.plugins.yinwutax.storage.TaxDataSnapshot;

public class YamlStorageFacade implements StorageFacade {

    private final JavaPlugin plugin;
    private final File dataFile;

    public YamlStorageFacade(JavaPlugin plugin, String dataFileName) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), dataFileName);
    }

    @Override
    public synchronized TaxDataSnapshot load() {
        TaxDataSnapshot snapshot = new TaxDataSnapshot();
        if (!dataFile.exists()) {
            return snapshot;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        loadUuidSet(yaml.getConfigurationSection("known-accounts"), snapshot.getKnownAccounts());
        loadBigDecimalMap(yaml.getConfigurationSection("income-totals"), snapshot.getIncomeTotals());
        loadIntegerMap(yaml.getConfigurationSection("exemptions"), snapshot.getExemptions());
        loadIntegerMap(yaml.getConfigurationSection("headcount-overrides"), snapshot.getHeadcountOverrides());
        loadBigDecimalMap(yaml.getConfigurationSection("last-tax-amounts"), snapshot.getLastTaxAmounts());
        loadPlayerIpHashes(yaml.getConfigurationSection("player-ip-hashes"), snapshot);
        loadIpOwners(yaml.getConfigurationSection("ip-owners"), snapshot);
        return snapshot;
    }

    @Override
    public synchronized void save(TaxDataSnapshot snapshot) {
        YamlConfiguration yaml = new YamlConfiguration();
        saveUuidSet(yaml.createSection("known-accounts"), snapshot.getKnownAccounts());
        saveBigDecimalMap(yaml.createSection("income-totals"), snapshot.getIncomeTotals());
        saveIntegerMap(yaml.createSection("exemptions"), snapshot.getExemptions());
        saveIntegerMap(yaml.createSection("headcount-overrides"), snapshot.getHeadcountOverrides());
        saveBigDecimalMap(yaml.createSection("last-tax-amounts"), snapshot.getLastTaxAmounts());
        savePlayerIpHashes(yaml.createSection("player-ip-hashes"), snapshot);
        saveIpOwners(yaml.createSection("ip-owners"), snapshot);

        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Unable to create plugin data directory.");
            }
            yaml.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save YinwuTax data file.", exception);
        }
    }

    private void loadUuidSet(ConfigurationSection section, Set<UUID> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            target.add(UUID.fromString(key));
        }
    }

    private void saveUuidSet(ConfigurationSection section, Set<UUID> source) {
        for (UUID uuid : source) {
            section.set(uuid.toString(), true);
        }
    }

    private void loadBigDecimalMap(ConfigurationSection section, java.util.Map<UUID, BigDecimal> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            target.put(UUID.fromString(key), parseBigDecimal(section.get(key), BigDecimal.ZERO));
        }
    }

    private void saveBigDecimalMap(ConfigurationSection section, java.util.Map<UUID, BigDecimal> source) {
        source.forEach((uuid, value) -> section.set(uuid.toString(), value));
    }

    private void loadIntegerMap(ConfigurationSection section, java.util.Map<UUID, Integer> target) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            target.put(UUID.fromString(key), section.getInt(key, 0));
        }
    }

    private void saveIntegerMap(ConfigurationSection section, java.util.Map<UUID, Integer> source) {
        source.forEach((uuid, value) -> section.set(uuid.toString(), value));
    }

    private void loadPlayerIpHashes(ConfigurationSection section, TaxDataSnapshot snapshot) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection inner = section.getConfigurationSection(key);
            Set<String> hashes = ConcurrentHashMap.newKeySet();
            if (inner != null) {
                hashes.addAll(inner.getKeys(false));
            }
            snapshot.getPlayerIpHashes().put(UUID.fromString(key), hashes);
        }
    }

    private void savePlayerIpHashes(ConfigurationSection section, TaxDataSnapshot snapshot) {
        snapshot.getPlayerIpHashes().forEach((uuid, hashes) -> {
            ConfigurationSection inner = section.createSection(uuid.toString());
            hashes.forEach(hash -> inner.set(hash, true));
        });
    }

    private void loadIpOwners(ConfigurationSection section, TaxDataSnapshot snapshot) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection inner = section.getConfigurationSection(key);
            Set<UUID> owners = ConcurrentHashMap.newKeySet();
            if (inner != null) {
                for (String uuidRaw : inner.getKeys(false)) {
                    owners.add(UUID.fromString(uuidRaw));
                }
            }
            snapshot.getIpOwners().put(key, owners);
        }
    }

    private void saveIpOwners(ConfigurationSection section, TaxDataSnapshot snapshot) {
        snapshot.getIpOwners().forEach((hash, owners) -> {
            ConfigurationSection inner = section.createSection(hash);
            owners.forEach(uuid -> inner.set(uuid.toString(), true));
        });
    }

    private BigDecimal parseBigDecimal(Object rawValue, BigDecimal fallback) {
        if (rawValue == null) {
            return fallback;
        }

        if (rawValue instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }

        if (rawValue instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }

        return new BigDecimal(String.valueOf(rawValue));
    }
}
