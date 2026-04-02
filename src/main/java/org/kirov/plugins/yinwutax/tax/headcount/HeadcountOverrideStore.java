package org.kirov.plugins.yinwutax.tax.headcount;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HeadcountOverrideStore {

    private final Map<UUID, Integer> overrideExtraAccounts = new ConcurrentHashMap<>();

    public HeadcountOverrideStore() {
    }

    public HeadcountOverrideStore(Map<UUID, Integer> initialValues) {
        overrideExtraAccounts.putAll(initialValues);
    }

    public void setOverrideExtraAccounts(UUID playerId, int extraAccounts) {
        overrideExtraAccounts.put(playerId, Math.max(0, extraAccounts));
    }

    public void clearOverride(UUID playerId) {
        overrideExtraAccounts.remove(playerId);
    }

    public OptionalInt getOverrideExtraAccounts(UUID playerId) {
        Integer value = overrideExtraAccounts.get(playerId);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    public Map<UUID, Integer> export() {
        return Map.copyOf(overrideExtraAccounts);
    }
}
