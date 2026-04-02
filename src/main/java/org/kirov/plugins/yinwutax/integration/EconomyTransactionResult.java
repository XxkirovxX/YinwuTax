package org.kirov.plugins.yinwutax.integration;

import java.math.BigDecimal;

public record EconomyTransactionResult(boolean success, BigDecimal requestedAmount, BigDecimal withdrawnAmount, BigDecimal resultingBalance, String errorMessage) {
}
