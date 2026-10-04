package org.kirov.plugins.yinwutax.platform.scheduler;

import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

/**
 * 在调度器构造阶段一次性解析服务端能力，并据此选择执行后端。
 *
 * <p>能力只解析一次，避免每次调度都做一次探测；探测失败时不会静默回落，而是先记录
 * 失败的方法与原因。
 */
final class SchedulerBackendResolver {

    private final Plugin plugin;
    private final SchedulerResolution resolution;

    SchedulerBackendResolver(Plugin plugin) {
        this.plugin = plugin;
        this.resolution = resolve(plugin);
    }

    SchedulerResolution resolution() {
        return resolution;
    }

    /**
     * 按解析结论给出执行后端。{@code FOLIA_BROKEN} 已在解析阶段记过日志，这里回落到 Bukkit。
     */
    SchedulerBackend backend() {
        if (resolution.kind() == SchedulerResolution.SchedulerBackendKind.FOLIA) {
            return new FoliaSchedulerBackend(plugin, resolution.capabilities());
        }

        return new BukkitSchedulerBackend(plugin);
    }

    private static SchedulerResolution resolve(Plugin plugin) {
        SchedulerCapabilities capabilities;
        try {
            capabilities = SchedulerProbe.probe(plugin);
        } catch (RuntimeException failure) {
            // 探测过程中出现意外异常：按错误状态处理，显式记录后回落。
            plugin.getLogger().log(
                Level.WARNING,
                "Failed to probe Folia scheduler support; falling back to Bukkit scheduling. "
                    + "Scheduled tasks will not run on the Folia schedulers.",
                failure
            );
            return SchedulerResolution.foliaBroken(SchedulerCapabilities.unavailable(), describe(failure));
        }

        if (capabilities.foliaReady()) {
            plugin.getLogger().fine("Folia schedulers detected; using Folia scheduling backends.");
            return SchedulerResolution.folia(capabilities);
        }

        if (capabilities.global().isPresent() || capabilities.async().isPresent()) {
            String reason = "server exposes Folia schedulers but required methods could not be bound";
            plugin.getLogger().warning(
                "Folia scheduler support looks present but is not usable (" + reason
                    + "); falling back to Bukkit scheduling."
            );
            return SchedulerResolution.foliaBroken(capabilities, reason);
        }

        // 非 Folia 服务端：这是预期结果，不需要任何错误日志。
        return SchedulerResolution.bukkit();
    }

    private static String describe(RuntimeException failure) {
        return failure.getClass().getName() + ": " + failure.getMessage();
    }
}
