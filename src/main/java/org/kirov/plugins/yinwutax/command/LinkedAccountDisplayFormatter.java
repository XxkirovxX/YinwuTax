package org.kirov.plugins.yinwutax.command;

import java.util.Collection;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Function;

public final class LinkedAccountDisplayFormatter {

    private LinkedAccountDisplayFormatter() {
    }

    public static String format(Collection<UUID> linkedAccounts, Function<UUID, String> nameResolver) {
        return linkedAccounts.stream()
            .map(playerId -> toDisplayName(playerId, nameResolver))
            .sorted(Comparator
                .comparing(DisplayName::resolved).reversed()
                .thenComparing(DisplayName::value, String.CASE_INSENSITIVE_ORDER))
            .map(DisplayName::value)
            .reduce((left, right) -> left + "、" + right)
            .orElse("无");
    }

    private static DisplayName toDisplayName(UUID playerId, Function<UUID, String> nameResolver) {
        String playerName = nameResolver.apply(playerId);
        if (playerName == null || playerName.isBlank()) {
            return new DisplayName(playerId.toString(), false);
        }
        return new DisplayName(playerName.trim(), true);
    }

    private record DisplayName(String value, boolean resolved) {
    }
}
