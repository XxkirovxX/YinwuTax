package org.kirov.plugins.yinwutax.tax.exemption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExemptionServiceTest {

    private final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void consumesOneExemptionPerStatement() {
        ExemptionStore store = new ExemptionStore();
        ExemptionService service = new ExemptionService(store);
        service.grant(playerId, 2);

        assertTrue(service.consumeForCurrentStatement(playerId));
        assertEquals(1, service.remaining(playerId));
        assertTrue(service.consumeForCurrentStatement(playerId));
        assertEquals(0, service.remaining(playerId));
    }

    @Test
    void doesNotConsumeWhenNoneRemain() {
        ExemptionStore store = new ExemptionStore();
        ExemptionService service = new ExemptionService(store);

        assertFalse(service.consumeForCurrentStatement(playerId));
        assertEquals(0, service.remaining(playerId));
    }
}
