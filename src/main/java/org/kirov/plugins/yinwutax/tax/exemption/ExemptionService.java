package org.kirov.plugins.yinwutax.tax.exemption;

import java.util.Objects;
import java.util.UUID;

public class ExemptionService {

    private final ExemptionStore store;

    public ExemptionService(ExemptionStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public void grant(UUID playerId, int count) {
        if (count <= 0) {
            return;
        }

        store.setRemaining(playerId, store.remaining(playerId) + count);
    }

    public int remaining(UUID playerId) {
        return store.remaining(playerId);
    }

    public void take(UUID playerId, int count) {
        if (count <= 0) {
            return;
        }

        int remaining = Math.max(0, store.remaining(playerId) - count);
        store.setRemaining(playerId, remaining);
    }

    public boolean consumeForCurrentStatement(UUID playerId) {
        int remaining = store.remaining(playerId);
        if (remaining <= 0) {
            return false;
        }

        store.setRemaining(playerId, remaining - 1);
        return true;
    }
}
