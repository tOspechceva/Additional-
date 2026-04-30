package digital.zil.hl.additional.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Накопление статистики по операциям в скользящих окнах времени (LAB9).
 * <p>
 * Окна задаются параметром {@code observability.windows} (например {@code 10s,30s,1m}).
 * Метод {@link #refresh()} вызывается по расписанию и пересчитывает снимки; при включённом логировании
 * пишет компактную сводку в лог приложения.
 */
@Service
public class ObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityService.class);

    private final ConcurrentLinkedQueue<TimedEvent> events = new ConcurrentLinkedQueue<>();
    private final List<WindowConfig> windows;
    private final AtomicReference<Map<String, Map<String, OperationStats>>> snapshots;
    private final long maxWindowMs;
    private final boolean logOnRefresh;
    private final boolean logEmptySnapshots;
    private final String applicationName;

    public ObservabilityService(
            @Value("${observability.windows:10s,30s,1m}") final String windowsConfig,
            @Value("${observability.log-on-refresh:true}") final boolean logOnRefresh,
            @Value("${observability.log-empty-snapshots:false}") final boolean logEmptySnapshots,
            @Value("${spring.application.name:additional-service}") final String applicationName
    ) {
        this.windows = parseWindows(windowsConfig);
        this.maxWindowMs = this.windows.stream().mapToLong(WindowConfig::windowMs).max().orElse(1000L);
        this.snapshots = new AtomicReference<>(emptySnapshot(this.windows));
        this.logOnRefresh = logOnRefresh;
        this.logEmptySnapshots = logEmptySnapshots;
        this.applicationName = Objects.requireNonNullElse(applicationName, "application");
    }

    public void recordSuccess(final String operation, final long durationNanos) {
        events.add(new TimedEvent(System.currentTimeMillis(), Objects.requireNonNull(operation), false, durationNanos));
    }

    public void recordFailure(final String operation, final long durationNanos) {
        events.add(new TimedEvent(System.currentTimeMillis(), Objects.requireNonNull(operation), true, durationNanos));
    }

    public Map<String, Map<String, OperationStats>> getAllWindows() {
        return snapshots.get();
    }

    public Map<String, OperationStats> getWindow(final String window) {
        final Map<String, OperationStats> stats = snapshots.get().get(window);
        if (stats == null) {
            throw new IllegalArgumentException("Неизвестное окно статистики: " + window);
        }
        return stats;
    }

    @Scheduled(fixedDelayString = "${observability.tick-ms:1000}")
    public void refresh() {
        final long now = System.currentTimeMillis();
        final long threshold = now - maxWindowMs;
        for (TimedEvent head = events.peek(); head != null && head.atMs() < threshold; head = events.peek()) {
            events.poll();
        }

        final List<TimedEvent> current = new ArrayList<>(events);
        final Map<String, Map<String, OperationStats>> computed = new LinkedHashMap<>();
        for (WindowConfig window : windows) {
            computed.put(window.label(), aggregateForWindow(current, now, window.windowMs()));
        }
        snapshots.set(computed);
        maybeLogSnapshot(computed);
    }

    private void maybeLogSnapshot(final Map<String, Map<String, OperationStats>> computed) {
        if (!logOnRefresh || !log.isInfoEnabled()) {
            return;
        }
        if (!logEmptySnapshots && isSnapshotEmpty(computed)) {
            return;
        }
        log.info("[{}] observability refresh: {}", applicationName, formatSnapshotForLog(computed));
    }

    private static boolean isSnapshotEmpty(final Map<String, Map<String, OperationStats>> snapshot) {
        for (Map<String, OperationStats> perWindow : snapshot.values()) {
            for (OperationStats st : perWindow.values()) {
                if (st.count() > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static String formatSnapshotForLog(final Map<String, Map<String, OperationStats>> snapshot) {
        final StringBuilder sb = new StringBuilder(512);
        for (Map.Entry<String, Map<String, OperationStats>> w : snapshot.entrySet()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(w.getKey()).append(":[");
            final Map<String, OperationStats> ops = w.getValue();
            if (ops.isEmpty()) {
                sb.append("empty");
            } else {
                boolean first = true;
                for (Map.Entry<String, OperationStats> e : ops.entrySet()) {
                    if (!first) {
                        sb.append("; ");
                    }
                    first = false;
                    final OperationStats s = e.getValue();
                    sb.append(e.getKey())
                            .append("{n=").append(s.count())
                            .append(",err=").append(s.errors())
                            .append(",avgMs=").append(String.format(java.util.Locale.ROOT, "%.2f", s.avgMs()))
                            .append(",minMs=").append(String.format(java.util.Locale.ROOT, "%.2f", s.minMs()))
                            .append(",maxMs=").append(String.format(java.util.Locale.ROOT, "%.2f", s.maxMs()))
                            .append(",rps=").append(String.format(java.util.Locale.ROOT, "%.2f", s.rps()))
                            .append('}');
                }
            }
            sb.append(']');
        }
        return sb.toString();
    }

    private static Map<String, OperationStats> aggregateForWindow(
            final List<TimedEvent> current,
            final long now,
            final long windowMs
    ) {
        final long start = now - windowMs;
        final Map<String, MutableStats> acc = new LinkedHashMap<>();
        for (TimedEvent event : current) {
            if (event.atMs() < start) {
                continue;
            }
            final MutableStats stats = acc.computeIfAbsent(event.operation(), ignored -> new MutableStats());
            stats.count++;
            if (event.failed()) {
                stats.errors++;
            }
            stats.totalNanos += event.durationNanos();
            stats.minNanos = Math.min(stats.minNanos, event.durationNanos());
            stats.maxNanos = Math.max(stats.maxNanos, event.durationNanos());
        }

        final Map<String, OperationStats> result = new LinkedHashMap<>();
        final double seconds = windowMs / 1000.0;
        for (Map.Entry<String, MutableStats> entry : acc.entrySet()) {
            final MutableStats s = entry.getValue();
            final double avgMs = (s.totalNanos / (double) s.count) / 1_000_000.0;
            final double minMs = s.minNanos / 1_000_000.0;
            final double maxMs = s.maxNanos / 1_000_000.0;
            final double rps = s.count / seconds;
            result.put(entry.getKey(), new OperationStats(s.count, s.errors, avgMs, minMs, maxMs, rps));
        }
        return result;
    }

    private static List<WindowConfig> parseWindows(final String windowsConfig) {
        final String[] parts = windowsConfig.split(",");
        final List<WindowConfig> parsed = new ArrayList<>();
        for (String raw : parts) {
            final String normalized = raw.trim().toLowerCase();
            if (normalized.isEmpty()) {
                continue;
            }
            parsed.add(new WindowConfig(normalized, parseDurationMs(normalized)));
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("observability.windows не должен быть пустым");
        }
        return parsed;
    }

    private static long parseDurationMs(final String value) {
        if (value.endsWith("ms")) {
            return Long.parseLong(value.substring(0, value.length() - 2));
        }
        if (value.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1))).toMillis();
        }
        if (value.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1))).toMillis();
        }
        throw new IllegalArgumentException("Неподдерживаемый формат окна: " + value);
    }

    private static Map<String, Map<String, OperationStats>> emptySnapshot(final List<WindowConfig> windows) {
        final Map<String, Map<String, OperationStats>> empty = new LinkedHashMap<>();
        for (WindowConfig window : windows) {
            empty.put(window.label(), Map.of());
        }
        return empty;
    }

    private record WindowConfig(String label, long windowMs) {
    }

    private record TimedEvent(long atMs, String operation, boolean failed, long durationNanos) {
    }

    private static final class MutableStats {
        private long count;
        private long errors;
        private long totalNanos;
        private long minNanos = Long.MAX_VALUE;
        private long maxNanos = Long.MIN_VALUE;
    }

    public record OperationStats(long count, long errors, double avgMs, double minMs, double maxMs, double rps) {
    }
}
