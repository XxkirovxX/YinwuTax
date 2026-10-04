package org.kirov.plugins.yinwutax.tax.headcount;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.kirov.plugins.yinwutax.storage.TaxDataSnapshot;

class IpHistoryTrackerTest {

    @Test
    void getLinkedAccountsReturnsPlayerItselfWhenNoHistoryExists() {
        IpHistoryTracker tracker = new IpHistoryTracker(new TaxDataSnapshot(), "salt");
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals(Set.of(playerId), tracker.getLinkedAccounts(playerId));
        assertEquals(1, tracker.getLinkedAccountCount(playerId));
    }

    @Test
    void getLinkedAccountsIncludesOtherPlayersSharingAnyRecordedHash() {
        IpHistoryTracker tracker = new IpHistoryTracker(new TaxDataSnapshot(), "salt");
        UUID primary = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID linked = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID unrelated = UUID.fromString("00000000-0000-0000-0000-000000000012");

        tracker.record(primary, "hash-a");
        tracker.record(primary, "hash-b");
        tracker.record(linked, "hash-b");
        tracker.record(unrelated, "hash-c");

        assertEquals(Set.of(primary, linked), tracker.getLinkedAccounts(primary));
        assertEquals(2, tracker.getLinkedAccountCount(primary));
    }
}
