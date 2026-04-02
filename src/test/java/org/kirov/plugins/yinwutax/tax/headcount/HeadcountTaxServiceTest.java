package org.kirov.plugins.yinwutax.tax.headcount;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class HeadcountTaxServiceTest {

    private final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void multipliesBaseIncomeTaxRateByExtraAccounts() {
        HeadcountOverrideStore overrideStore = new HeadcountOverrideStore();
        HeadcountTaxService service = new HeadcountTaxService(overrideStore, new BigDecimal("0.20"));

        BigDecimal finalRate = service.resolveFinalIncomeTaxRate(playerId, new BigDecimal("0.03"), 4);

        assertEquals(new BigDecimal("0.0480"), finalRate);
    }

    @Test
    void overrideWinsOverDetectedLinkedAccountCount() {
        HeadcountOverrideStore overrideStore = new HeadcountOverrideStore();
        overrideStore.setOverrideExtraAccounts(playerId, 1);
        HeadcountTaxService service = new HeadcountTaxService(overrideStore, new BigDecimal("0.20"));

        BigDecimal finalRate = service.resolveFinalIncomeTaxRate(playerId, new BigDecimal("0.03"), 6);

        assertEquals(new BigDecimal("0.0360"), finalRate);
    }

    @Test
    void leavesBaseRateUnchangedWhenNoExtraAccountsExist() {
        HeadcountOverrideStore overrideStore = new HeadcountOverrideStore();
        HeadcountTaxService service = new HeadcountTaxService(overrideStore, new BigDecimal("0.20"));

        BigDecimal finalRate = service.resolveFinalIncomeTaxRate(playerId, new BigDecimal("0.03"), 1);

        assertEquals(new BigDecimal("0.0300"), finalRate);
    }
}
