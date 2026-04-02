package org.kirov.plugins.yinwutax.integration;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;

public interface EconomyGateway {

    EconomyCapabilities capabilities();

    BigDecimal getBalance(UUID accountId);

    Collection<UUID> getKnownAccounts();

    EconomyTransactionResult withdrawUpTo(UUID accountId, BigDecimal requestedAmount, String reason);

    String format(BigDecimal amount);
}
