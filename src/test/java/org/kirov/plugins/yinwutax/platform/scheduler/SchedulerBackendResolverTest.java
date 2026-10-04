package org.kirov.plugins.yinwutax.platform.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 后端选择规则的纯逻辑测试：不接触任何服务端对象。
 */
class SchedulerBackendResolverTest {

    private static final MethodHandle DUMMY_HANDLE = dummyHandle();

    @Test
    void bukkitResolutionFallsBackToBukkitKind() {
        SchedulerResolution resolution = SchedulerResolution.bukkit();

        assertEquals(SchedulerResolution.SchedulerBackendKind.BUKKIT, resolution.kind());
        assertTrue(resolution.failureReason().isEmpty());
    }

    @Test
    void fullyBoundFoliaCapabilitiesResolveToFolia() {
        SchedulerResolution resolution = SchedulerResolution.folia(completeCapabilities());

        assertEquals(SchedulerResolution.SchedulerBackendKind.FOLIA, resolution.kind());
        assertTrue(resolution.foliaReady());
    }

    @Test
    void foliaCapabilitiesExposeAllSchedulerMethods() {
        SchedulerCapabilities.SchedulerMethods methods = methods();

        assertNotNull(methods.oneShot());
        assertNotNull(methods.delayed());
        assertNotNull(methods.repeating());
        assertNotNull(methods.cancelTasks());
    }

    @Test
    void brokenFoliaBindingResolvesToFoliaBrokenAndKeepsReason() {
        SchedulerResolution resolution = SchedulerResolution.foliaBroken(
            completeCapabilities(),
            "runAtFixedRate could not be bound"
        );

        assertEquals(SchedulerResolution.SchedulerBackendKind.FOLIA_BROKEN, resolution.kind());
        assertFalse(resolution.foliaReady());
        assertEquals(Optional.of("runAtFixedRate could not be bound"), resolution.failureReason());
    }

    @Test
    void partiallyAvailableFoliaCapabilitiesAreBroken() {
        SchedulerResolution resolution = SchedulerResolution.folia(
            new SchedulerCapabilities(Optional.of(methods()), Optional.empty())
        );

        assertEquals(SchedulerResolution.SchedulerBackendKind.FOLIA_BROKEN, resolution.kind());
    }

    private static SchedulerCapabilities completeCapabilities() {
        return new SchedulerCapabilities(Optional.of(methods()), Optional.of(methods()));
    }

    private static SchedulerCapabilities.SchedulerMethods methods() {
        return new SchedulerCapabilities.SchedulerMethods(
            DUMMY_HANDLE,
            DUMMY_HANDLE,
            DUMMY_HANDLE,
            DUMMY_HANDLE,
            0
        );
    }

    private static MethodHandle dummyHandle() {
        try {
            return java.lang.invoke.MethodHandles.lookup().findStatic(
                SchedulerBackendResolverTest.class,
                "noop",
                java.lang.invoke.MethodType.methodType(void.class)
            );
        } catch (NoSuchMethodException | IllegalAccessException failure) {
            throw new IllegalStateException("Failed to build dummy method handle", failure);
        }
    }

    private static void noop() {
    }
}
