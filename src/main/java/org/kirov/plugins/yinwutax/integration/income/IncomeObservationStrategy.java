package org.kirov.plugins.yinwutax.integration.income;

public interface IncomeObservationStrategy {

    boolean isActive();

    boolean isPrecise();

    void start();

    void stop();
}
