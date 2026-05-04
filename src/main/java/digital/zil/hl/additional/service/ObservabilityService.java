package digital.zil.hl.additional.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue; // потокобезопасная очередь для событий
import java.util.concurrent.atomic.AtomicReference; // атомарная ссылка для безопасного обновления snapshot
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value; // инжект значений из application.properties
import org.springframework.scheduling.annotation.Scheduled; // аннотация для периодического выполнения
import org.springframework.stereotype.Service;

@Service
public class ObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityService.class); // логгер

    // Очередь событий: потокобезопасная, добавление/чтение без блокировок
    private final ConcurrentLinkedQueue<TimedEvent> events = new ConcurrentLinkedQueue<>();

    private final List<WindowConfig> windows; // конфигурация временных окон (10s, 30s, 1m...)
    private final AtomicReference<Map<String, Map<String, OperationStats>>> snapshots; // кэш расчитанных метрик
    private final long maxWindowMs; // самое большое окно — нужно для очистки старых событий
    private final boolean logOnRefresh; // флаг: писать ли логи при обновлении
    private final boolean logEmptySnapshots; // флаг: писать ли логи, если метрик нет
    private final String applicationName; // имя приложения для логов

    public ObservabilityService(
            @Value("${observability.windows:10s,30s,1m}") final String windowsConfig, // парсим окна из конфига
            @Value("${observability.log-on-refresh:true}") final boolean logOnRefresh,
            @Value("${observability.log-empty-snapshots:false}") final boolean logEmptySnapshots,
            @Value("${spring.application.name:additional-service}") final String applicationName
    ) {
        this.windows = parseWindows(windowsConfig); // разбираем строку "10s,30s,1m" → список WindowConfig
        this.maxWindowMs = this.windows.stream().mapToLong(WindowConfig::windowMs).max().orElse(1000L); // находим самое длинное окно
        this.snapshots = new AtomicReference<>(emptySnapshot(this.windows)); // инициализируем пустой snapshot
        this.logOnRefresh = logOnRefresh;
        this.logEmptySnapshots = logEmptySnapshots;
        this.applicationName = Objects.requireNonNullElse(applicationName, "application"); // fallback, если имя не задано
    }

    // Запись успешного выполнения операции: добавляем событие в очередь
    public void recordSuccess(final String operation, final long durationNanos) {
        events.add(new TimedEvent(System.currentTimeMillis(), Objects.requireNonNull(operation), false, durationNanos));
        //                                                    ↑ время, ↑ имя операции, ↑ не ошибка, ↑ длительность
    }

    // Запись ошибки: аналогично, но с флагом failed = true
    public void recordFailure(final String operation, final long durationNanos) {
        events.add(new TimedEvent(System.currentTimeMillis(), Objects.requireNonNull(operation), true, durationNanos));
    }

    // Возвращает кэш метрик по всем окнам (читаем атомарно)
    public Map<String, Map<String, OperationStats>> getAllWindows() {
        return snapshots.get();
    }

    // Возвращает метрики для конкретного окна, или кидает исключение, если окно неизвестно
    public Map<String, OperationStats> getWindow(final String window) {
        final Map<String, OperationStats> stats = snapshots.get().get(window);
        if (stats == null) {
            throw new IllegalArgumentException("Неизвестное окно статистики: " + window);
        }
        return stats;
    }

    // Вызывается по расписанию (по умолчанию каждые 10 сек): пересчитывает метрики и чистит старые события
    @Scheduled(fixedDelayString = "${observability.tick-ms:10000}")
    public void refresh() {
        final long now = System.currentTimeMillis(); // текущее время
        final long threshold = now - maxWindowMs; // граница: события старше этого времени можно удалять

        // Удаляем из головы очереди все события, которые вышли за пределы самого большого окна
        for (TimedEvent head = events.peek(); head != null && head.atMs() < threshold; head = events.peek()) {
            events.poll();
        }

        // Копируем текущие события в список для агрегации (чтобы не блокировать очередь во время расчётов)
        final List<TimedEvent> current = new ArrayList<>(events);
        final Map<String, Map<String, OperationStats>> computed = new LinkedHashMap<>(); // результат пересчёта

        // Для каждого окна считаем статистику
        for (WindowConfig window : windows) {
            computed.put(window.label(), aggregateForWindow(current, now, window.windowMs()));
        }

        snapshots.set(computed); // атомарно заменяем старый snapshot на новый
        maybeLogSnapshot(computed); // опционально пишем в лог
    }

    // Пишет в лог, если включено логирование и снапшот не пустой (или разрешено логировать пустые)
    private void maybeLogSnapshot(final Map<String, Map<String, OperationStats>> computed) {
        if (!logOnRefresh || !log.isInfoEnabled()) {
            return;
        }
        if (!logEmptySnapshots && isSnapshotEmpty(computed)) {
            return;
        }
        log.info("[{}] observability refresh\n{}", applicationName, formatSnapshotReadable(computed));
    }

    // Проверяет, есть ли в снапшоте хоть одна метрика с count > 0
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

    // Многострочный вывод для консоли: окна по порядку конфига, операции по имени
    private static String formatSnapshotReadable(final Map<String, Map<String, OperationStats>> snapshot) {
        final StringBuilder sb = new StringBuilder(1024);
        for (Map.Entry<String, Map<String, OperationStats>> w : snapshot.entrySet()) {
            sb.append("  window ").append(w.getKey()).append(":\n");
            final Map<String, OperationStats> ops = w.getValue();
            if (ops.isEmpty()) {
                sb.append("    (no events)\n");
                continue;
            }
            final List<Map.Entry<String, OperationStats>> sorted = new ArrayList<>(ops.entrySet());
            sorted.sort(Map.Entry.comparingByKey());
            for (Map.Entry<String, OperationStats> e : sorted) {
                final OperationStats s = e.getValue();
                sb.append(String.format(
                        java.util.Locale.ROOT,
                        "    %-52s  n=%5d  err=%3d  avg=%8.2f ms  min=%8.2f ms  max=%8.2f ms  rps=%6.2f%n",
                        e.getKey(),
                        s.count(),
                        s.errors(),
                        s.avgMs(),
                        s.minMs(),
                        s.maxMs(),
                        s.rps()));
            }
        }
        return sb.toString();
    }

    // Агрегирует события за указанное окно: считает count, errors, avg/min/max времени, RPS
    private static Map<String, OperationStats> aggregateForWindow(
            final List<TimedEvent> current,
            final long now,
            final long windowMs
    ) {
        final long start = now - windowMs; // начало временного окна
        final Map<String, MutableStats> acc = new LinkedHashMap<>(); // аккумулятор для промежуточных расчётов

        for (TimedEvent event : current) {
            if (event.atMs() < start) {
                continue; // пропускаем события, не попавшие в окно
            }
            // Получаем или создаём статистику для операции
            final MutableStats stats = acc.computeIfAbsent(event.operation(), ignored -> new MutableStats());
            stats.count++; // инкремент общего счётчика
            if (event.failed()) {
                stats.errors++; // если ошибка — инкремент счётчика ошибок
            }
            stats.totalNanos += event.durationNanos(); // суммируем время для среднего
            stats.minNanos = Math.min(stats.minNanos, event.durationNanos()); // обновляем минимум
            stats.maxNanos = Math.max(stats.maxNanos, event.durationNanos()); // обновляем максимум
        }

        // Преобразуем промежуточные данные в неизменяемые OperationStats
        final Map<String, OperationStats> result = new LinkedHashMap<>();
        final double seconds = windowMs / 1000.0; // переводим миллисекунды окна в секунды для RPS
        for (Map.Entry<String, MutableStats> entry : acc.entrySet()) {
            final MutableStats s = entry.getValue();
            final double avgMs = (s.totalNanos / (double) s.count) / 1_000_000.0; // наносекунды → миллисекунды
            final double minMs = s.minNanos / 1_000_000.0;
            final double maxMs = s.maxNanos / 1_000_000.0;
            final double rps = s.count / seconds; // запросов в секунду
            result.put(entry.getKey(), new OperationStats(s.count, s.errors, avgMs, minMs, maxMs, rps));
        }
        return result;
    }

    // Парсит строку конфигурации окон: "10s,30s,1m" → список WindowConfig
    private static List<WindowConfig> parseWindows(final String windowsConfig) {
        final String[] parts = windowsConfig.split(","); // разбиваем по запятой
        final List<WindowConfig> parsed = new ArrayList<>();
        for (String raw : parts) {
            final String normalized = raw.trim().toLowerCase(); // нормализуем: убираем пробелы, нижний регистр
            if (normalized.isEmpty()) {
                continue;
            }
            parsed.add(new WindowConfig(normalized, parseDurationMs(normalized))); // парсим длительность в мс
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("observability.windows не должен быть пустым");
        }
        return parsed;
    }

    // Конвертирует строку вида "10s", "5m", "100ms" в миллисекунды
    private static long parseDurationMs(final String value) {
        if (value.endsWith("ms")) {
            return Long.parseLong(value.substring(0, value.length() - 2)); // "100ms" → 100
        }
        if (value.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(value.substring(0, value.length() - 1))).toMillis(); // "30s" → 30000
        }
        if (value.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(value.substring(0, value.length() - 1))).toMillis(); // "1m" → 60000
        }
        throw new IllegalArgumentException("Неподдерживаемый формат окна: " + value);
    }

    // Создаёт пустой snapshot: для каждого окна — пустая мапа операций
    private static Map<String, Map<String, OperationStats>> emptySnapshot(final List<WindowConfig> windows) {
        final Map<String, Map<String, OperationStats>> empty = new LinkedHashMap<>();
        for (WindowConfig window : windows) {
            empty.put(window.label(), Map.of()); // Map.of() — неизменяемая пустая мапа
        }
        return empty;
    }

    // Конфигурация одного окна: лейбл ("1m") и длительность в мс (60000)
    private record WindowConfig(String label, long windowMs) {
    }

    // Событие: время, имя операции, флаг ошибки, длительность в наносекундах
    private record TimedEvent(long atMs, String operation, boolean failed, long durationNanos) {
    }

    // Вспомогательный мутабельный класс для накопления статистики внутри aggregateForWindow
    private static final class MutableStats {
        private long count;          // всего вызовов
        private long errors;         // ошибок
        private long totalNanos;     // сумма длительностей для среднего
        private long minNanos = Long.MAX_VALUE; // минимум (инициализируем максимумом для корректного Math.min)
        private long maxNanos = Long.MIN_VALUE; // максимум (инициализируем минимумом для корректного Math.max)
    }

    // Публичный неизменяемый DTO с метриками операции: count, errors, avg/min/max в мс, RPS
    public record OperationStats(long count, long errors, double avgMs, double minMs, double maxMs, double rps) {
    }
}