package org.kirov.plugins.yinwutax.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record YinwuTaxConfig(
    IncomeTaxSettings incomeTax,
    HeadcountTaxSettings headcountTax,
    WealthTaxSettings wealthTax,
    ExemptionSettings exemption,
    StorageSettings storage,
    VelocitySettings velocity
) {

    public static YinwuTaxConfig from(FileConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");

        return new YinwuTaxConfig(
            new IncomeTaxSettings(
                configuration.getBoolean("income-tax.enabled", true),
                DurationParser.parse(configuration.getString("income-tax.period", "24h")),
                loadBrackets(configuration.getConfigurationSection("income-tax.brackets")),
                configuration.getBoolean("income-tax.exclude-set-reset", true),
                configuration.getBoolean("income-tax.exclude-admin-operations", true)
            ),
            new HeadcountTaxSettings(
                configuration.getBoolean("headcount-tax.enabled", true),
                getBigDecimal(configuration, "headcount-tax.per-account-extra-rate", new BigDecimal("0.20"))
            ),
            new WealthTaxSettings(
                configuration.getBoolean("wealth-tax.enabled", true),
                DurationParser.parse(configuration.getString("wealth-tax.period", "24h")),
                loadBrackets(configuration.getConfigurationSection("wealth-tax.brackets"))
            ),
            new ExemptionSettings(
                configuration.getBoolean("exemption.enabled", true),
                configuration.getInt("exemption.max-grant-per-command", 64)
            ),
            new StorageSettings(
                configuration.getString("storage.ip-hash-salt", "change-me"),
                configuration.getString("storage.data-file", "tax-data.yml")
            ),
            new VelocitySettings(configuration.getBoolean("velocity.enabled", false))
        );
    }

    private static List<TaxBracket> loadBrackets(ConfigurationSection section) {
        List<TaxBracket> brackets = new ArrayList<>();
        if (section == null) {
            return brackets;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection bracketSection = section.getConfigurationSection(key);
            if (bracketSection == null) {
                continue;
            }

            BigDecimal min = getBigDecimal(bracketSection, "min", BigDecimal.ZERO);
            BigDecimal max = bracketSection.contains("max") ? getBigDecimal(bracketSection, "max", null) : null;
            BigDecimal rate = getBigDecimal(bracketSection, "rate", BigDecimal.ZERO);
            brackets.add(new TaxBracket(min, max, rate));
        }

        return brackets;
    }

    private static BigDecimal getBigDecimal(ConfigurationSection section, String path, BigDecimal fallback) {
        Object value = section.get(path);
        if (value == null) {
            return fallback;
        }

        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }

        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }

        return new BigDecimal(String.valueOf(value));
    }

    public record IncomeTaxSettings(
        boolean enabled,
        Duration period,
        List<TaxBracket> brackets,
        boolean excludeSetReset,
        boolean excludeAdminOperations
    ) {
    }

    public record HeadcountTaxSettings(boolean enabled, BigDecimal perAccountExtraRate) {
    }

    public record WealthTaxSettings(boolean enabled, Duration period, List<TaxBracket> brackets) {
    }

    public record ExemptionSettings(boolean enabled, int maxGrantPerCommand) {
    }

    public record StorageSettings(String ipHashSalt, String dataFileName) {
    }

    public record VelocitySettings(boolean enabled) {
    }
}
