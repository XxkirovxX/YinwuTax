package org.kirov.plugins.yinwutax.integration;

public record EconomyCapabilities(boolean available, String providerName, boolean preciseIncomeTracking) {

    public boolean isOperational() {
        return available;
    }

    public boolean supportsPreciseIncomeTracking() {
        return available && preciseIncomeTracking;
    }
}
