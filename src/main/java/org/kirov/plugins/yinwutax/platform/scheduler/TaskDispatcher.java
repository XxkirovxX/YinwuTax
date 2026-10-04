package org.kirov.plugins.yinwutax.platform.scheduler;

import java.time.Duration;

/**
 * 平台无关的调度契约。
 *
 * <p>刻意不出现任何 Paper/Folia 类型：实现类负责在 Folia 与 Bukkit 调度之间做选择。
 */
public interface TaskDispatcher extends AutoCloseable {

    /** 在全局调度域执行一次性任务。 */
    void runGlobal(Runnable task);

    /** 在异步线程执行一次性任务。 */
    void runAsync(Runnable task);

    /** 在全局调度域执行一次延迟任务。 */
    void scheduleGlobalDelayed(TaskContext context, Runnable task);

    /** 在全局调度域注册固定周期任务，同名任务会先取消旧任务。 */
    void scheduleGlobalRepeating(TaskContext context, Runnable task);

    /** 取消指定 key 注册的任务。 */
    void cancel(String key);

    @Override
    void close();

    /** 把时长换算为 Minecraft tick，至少 1 tick，避免 0 周期任务。 */
    static long toTicks(Duration duration) {
        return Math.max(1L, duration.toMillis() / 50L);
    }
}
