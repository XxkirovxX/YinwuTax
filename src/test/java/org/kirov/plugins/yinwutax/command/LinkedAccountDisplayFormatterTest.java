package org.kirov.plugins.yinwutax.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LinkedAccountDisplayFormatterTest {

    @Test
    void formatUsesPlayerNamesWhenAvailableAndFallsBackToUuid() {
        UUID alice = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID unknown = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

        String formatted = LinkedAccountDisplayFormatter.format(
            List.of(unknown, alice),
            playerId -> alice.equals(playerId) ? "Alice" : null
        );

        assertEquals("Alice、00000000-0000-0000-0000-0000000000bb", formatted);
    }

    @Test
    void formatTreatsBlankNamesAsMissing() {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-0000000000cc");

        String formatted = LinkedAccountDisplayFormatter.format(List.of(playerId), ignored -> "  ");

        assertEquals("00000000-0000-0000-0000-0000000000cc", formatted);
    }

    @Test
    void formatSortsResolvedNamesBeforeUuidFallbacks() {
        UUID charlie = UUID.fromString("00000000-0000-0000-0000-0000000000cd");
        UUID alice = UUID.fromString("00000000-0000-0000-0000-0000000000ce");
        UUID unknown = UUID.fromString("00000000-0000-0000-0000-0000000000cf");

        String formatted = LinkedAccountDisplayFormatter.format(
            List.of(unknown, charlie, alice),
            playerId -> {
                if (alice.equals(playerId)) {
                    return "Alice";
                }
                if (charlie.equals(playerId)) {
                    return "Charlie";
                }
                return null;
            }
        );

        assertEquals("Alice、Charlie、00000000-0000-0000-0000-0000000000cf", formatted);
    }
}
