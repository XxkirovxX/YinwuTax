package org.kirov.plugins.yinwutax.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EconomyCapabilitiesTest {

    @Test
    void preciseTrackingRequiresEconomyAvailability() {
        EconomyCapabilities unavailable = new EconomyCapabilities(false, "none", false);
        EconomyCapabilities precise = new EconomyCapabilities(true, "iConomyUnlocked", true);

        assertFalse(unavailable.isOperational());
        assertFalse(unavailable.supportsPreciseIncomeTracking());
        assertTrue(precise.isOperational());
        assertTrue(precise.supportsPreciseIncomeTracking());
    }
}
