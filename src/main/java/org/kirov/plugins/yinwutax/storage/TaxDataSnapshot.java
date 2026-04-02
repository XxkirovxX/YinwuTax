package org.kirov.plugins.yinwutax.storage;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TaxDataSnapshot {

    private final Set<UUID> knownAccounts = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BigDecimal> incomeTotals = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> exemptions = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> headcountOverrides = new ConcurrentHashMap<>();
    private final Map<UUID, BigDecimal> lastTaxAmounts = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerIpHashes = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> ipOwners = new ConcurrentHashMap<>();

    public Set<UUID> getKnownAccounts() {
        return knownAccounts;
    }

    public Map<UUID, BigDecimal> getIncomeTotals() {
        return incomeTotals;
    }

    public Map<UUID, Integer> getExemptions() {
        return exemptions;
    }

    public Map<UUID, Integer> getHeadcountOverrides() {
        return headcountOverrides;
    }

    public Map<UUID, BigDecimal> getLastTaxAmounts() {
        return lastTaxAmounts;
    }

    public Map<UUID, Set<String>> getPlayerIpHashes() {
        return playerIpHashes;
    }

    public Map<String, Set<UUID>> getIpOwners() {
        return ipOwners;
    }
}
