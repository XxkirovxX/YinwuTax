package org.kirov.plugins.yinwutax.config;

import java.time.Duration;
import java.util.Locale;

public final class DurationParser {

    private DurationParser() {
    }

    public static Duration parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Duration value cannot be blank.");
        }

        String value = rawValue.trim().toLowerCase(Locale.ROOT);
        long amount = Long.parseLong(value.substring(0, value.length() - 1));
        char suffix = value.charAt(value.length() - 1);

        return switch (suffix) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Unsupported duration suffix: " + suffix);
        };
    }
}
