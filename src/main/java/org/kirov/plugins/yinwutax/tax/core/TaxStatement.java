package org.kirov.plugins.yinwutax.tax.core;

import java.math.BigDecimal;
import java.util.UUID;

public record TaxStatement(
    TaxStatementType type,
    UUID playerId,
    BigDecimal taxableBase,
    BigDecimal taxRate,
    BigDecimal taxAmount
) {
}
