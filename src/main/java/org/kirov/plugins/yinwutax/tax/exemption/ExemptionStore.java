package org.kirov.plugins.yinwutax.tax.exemption;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ExemptionStore {

    private final Map<UUID, Integer> exemptions = new ConcurrentHashMap<>();

    public ExemptionStore() {
    }

    public ExemptionStore(Map<UUID, Integer> initialValues) {
        exemptions.putAll(initialValues);
    }

    public int remaining(UUID playerId) {
        return exemptions.getOrDefault(playerId, 0);
    }

    public void setRemaining(UUID playerId, int count) {
        if (count <= 0) {
            exemptions.remove(playerId);
            return;
        }

        exemptions.put(playerId, count);
    }

    public Map<UUID, Integer> export() {
        return Map.copyOf(exemptions);
    }
}
