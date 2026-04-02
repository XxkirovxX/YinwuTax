package org.kirov.plugins.yinwutax.platform.scheduler;

import java.time.Duration;

public record TaskContext(String key, Duration initialDelay, Duration period) {
}
