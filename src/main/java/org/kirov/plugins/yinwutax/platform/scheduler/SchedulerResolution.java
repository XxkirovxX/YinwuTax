package org.kirov.plugins.yinwutax.platform.scheduler;

import java.util.Optional;

/**
 * 探测结论：区分 "非 Folia"、"Folia 可用" 与 "Folia 存在但方法绑定失败"。
 *
 * <p>{@link SchedulerBackendKind#FOLIA_BROKEN} 是错误状态，必须显式记日志后再回落，
 * 不能静默回落到 Bukkit 调度。
 */
record SchedulerResolution(SchedulerCapabilities capabilities, Optional<String> failure) {

    /** 后端种类。 */
    enum SchedulerBackendKind {
        BUKKIT,
        FOLIA,
        FOLIA_BROKEN
    }

    static SchedulerResolution bukkit() {
        return new SchedulerResolution(SchedulerCapabilities.unavailable(), Optional.empty());
    }

    static SchedulerResolution folia(SchedulerCapabilities capabilities) {
        return new SchedulerResolution(capabilities, Optional.empty());
    }

    static SchedulerResolution foliaBroken(SchedulerCapabilities capabilities, String failure) {
        return new SchedulerResolution(capabilities, Optional.of(failure));
    }

    SchedulerBackendKind kind() {
        if (failure.isPresent() || partiallyAvailable()) {
            return SchedulerBackendKind.FOLIA_BROKEN;
        }

        return capabilities.foliaReady() ? SchedulerBackendKind.FOLIA : SchedulerBackendKind.BUKKIT;
    }

    boolean foliaReady() {
        return kind() == SchedulerBackendKind.FOLIA;
    }

    Optional<String> failureReason() {
        return failure;
    }

    /** Folia 调度器存在但只有一个绑定成功：同样是错误状态。 */
    private boolean partiallyAvailable() {
        return capabilities.global().isPresent() != capabilities.async().isPresent();
    }
}
