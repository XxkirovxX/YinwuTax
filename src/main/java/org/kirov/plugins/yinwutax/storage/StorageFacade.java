package org.kirov.plugins.yinwutax.storage;

public interface StorageFacade {

    TaxDataSnapshot load();

    void save(TaxDataSnapshot snapshot);
}
