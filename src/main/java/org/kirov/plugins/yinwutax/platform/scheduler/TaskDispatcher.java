package org.kirov.plugins.yinwutax.platform.scheduler;

import java.time.Duration;

public interface TaskDispatcher extends AutoCloseable {

    void runGlobal(Runnable task);

    void runAsync(Runnable task);

    void scheduleGlobalRepeating(TaskContext context, Runnable task);

    void cancel(String key);

    @Override
    void close();

    static long toTicks(Duration duration) {
        long ticks = Math.max(1L, duration.toMillis() / 50L);
        return ticks == 0L ? 1L : ticks;
    }
}
