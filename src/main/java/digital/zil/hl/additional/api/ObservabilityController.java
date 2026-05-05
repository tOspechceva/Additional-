package digital.zil.hl.additional.api;

import digital.zil.hl.additional.service.ObservabilityService;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер для предоставления агрегированной observability-статистики.
 * <p>
 * <b>Назначение (LAB9):</b> экспонировать метрики производительности и надёжности
 * для мониторинга работы приложения через Prometheus/Grafana или другие системы.
 * <p>
 * <b>Предоставляемые данные:</b>
 * <ul>
 *     <li>Статистика по временным окнам (например: {@code "1m"}, {@code "5m"}, {@code "1h"});</li>
 *     <li>Метрики успешных/неуспешных операций;</li>
 *     <li>Время выполнения запросов (латентность).</li>
 * </ul>
 * <p>
 * @see ObservabilityService — сервис, управляющий сбором и агрегацией метрик
 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    /** Сервис для работы с observability-метриками. */
    private final ObservabilityService observabilityService;

    /**
     * Конструктор с внедрением зависимости (Constructor Injection).

     * @param observabilityService сервис для сбора и предоставления метрик
     */
    public ObservabilityController(final ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    /**
     * Возвращает статистику observability для указанного временного окна.
     * <p>
     * <b>Параметры запроса:</b>
     * <ul>
     *     <li>{@code window} (опционально) — идентификатор временного окна:
     *         <ul>
     *             <li>{@code null} или пустое значение → вернуть статистику по всем окнам;</li>
     *             <li>{@code "1m"} → метрики за последнюю минуту;</li>
     *             <li>{@code "5m"} → метрики за последние 5 минут;</li>
     *             <li>{@code "1h"} → метрики за последний час.</li>
     *         </ul>
     *     </li>
     * </ul>
     * <p>
     * <b>Формат ответа:</b>
     * <pre>{@code
     * // Если window не указан:
     * {
     *   "1m": {
     *     "controller:observability:get": {
     *       "count": 42,
     *       "successCount": 40,
     *       "failureCount": 2,
     *       "avgDurationNs": 12500000
     *     }
     *   },
     *   "5m": { ... }
     * }
     *
     * // Если window="1m":
     * {
     *   "controller:observability:get": {
     *     "count": 42,
     *     "successCount": 40,
     *     "failureCount": 2,
     *     "avgDurationNs": 12500000
     *   }
     * }
     * }</pre>
     * <p>
     * <b>Метрики:</b> каждый вызов этого эндпоинта также измеряется
     * через метод {@code timed()} и записывается в {@link ObservabilityService}
     * под именем операции {@code "controller:observability:get"}.
     *
     * @param window опциональный параметр для фильтрации по временному окну
     * @return {@link Map} со статистикой или {@link Object} (в зависимости от параметра)
     * @see ObservabilityService#getAllWindows()
     * @see ObservabilityService#getWindow(String)
     */
    @GetMapping
    public Object getStats(@RequestParam(required = false) final String window) {
        // Обёртываем бизнес-логику в timed() для автоматического сбора метрик
        // о времени выполнения и успешности этого запроса
        return timed("controller:observability:get", () -> {
            // Если window не указан или пустой — возвращаем статистику по всем окнам
            if (window == null || window.isBlank()) {
                return observabilityService.getAllWindows();
            }
            // Иначе — возвращаем статистику только для указанного окна
            // trim() удаляет лишние пробелы, toLowerCase() нормализует регистр
            return observabilityService.getWindow(window.trim().toLowerCase());
        });
    }

    /**
     * Возвращает статистику по всем временным окнам (явный эндпоинт для {@code /windows}).
     * <p>
     * <b>Зачем отдельный метод:</b>
     * <ul>
     *     <li>Явный контракт: возвращает конкретный тип {@code Map<...>}, а не {@code Object};</li>
     *     <li>Удобно для Swagger/OpenAPI: генерация документации будет точнее;</li>
     *     <li>Можно вызвать напрямую из других компонентов, если нужно.</li>
     * </ul>
     * <p>
     * <b>Структура ответа:</b>
     * <pre>{@code
     * {
     *   "window_id": {
     *     "operation_name": {
     *       "count": <общее число вызовов>,
     *       "successCount": <число успешных>,
     *       "failureCount": <число ошибок>,
     *       "avgDurationNs": <среднее время выполнения в наносекундах>
     *     }
     *   }
     * }
     * }</pre>
     *
     * @return вложенная {@link Map}: {@code окно → операция → статистика}
     * @see ObservabilityService.OperationStats — DTO с метриками одной операции
     */
    @GetMapping("/windows")
    public Map<String, Map<String, ObservabilityService.OperationStats>> getAllWindows() {
        // Делегируем сервису, оборачивая вызов в timed() для сбора метрик
        // Метод-ссылка observabilityService::getAllWindows передаётся как Supplier
        return timed("controller:observability:getAllWindows", observabilityService::getAllWindows);
    }

    /**
     * Универсальная обёртка для измерения времени выполнения операции
     * и записи результата в observability-сервис.
     * <p>
     * <b>Паттерн:</b> Decorator + Template Method.
     * Метод принимает любую логику через {@link Supplier} и добавляет к ней:
     * <ul>
     *     <li>Замер времени начала и окончания выполнения;</li>
     *     <li>Запись метрики успеха или ошибки в зависимости от исхода;</li>
     *     <li>Проброс исключения наружу, если оно произошло.</li>
     * </ul>
     * <p>
     * <b>Почему {@code System.nanoTime()}:</b>
     * <ul>
     *     <li>Высокая точность (наносекунды) важна для измерения коротких операций;</li>
     *     <li>Не зависит от изменений системного времени (в отличие от {@code currentTimeMillis()});</li>
     *     <li>Предназначен именно для измерения интервалов, а не абсолютного времени.</li>
     * </ul>
     * <p>
     * <b>Потокобезопасность:</b> метод не хранит состояние, поэтому безопасен
     * для одновременного вызова из нескольких потоков (при условии, что
     * {@link ObservabilityService} также потокобезопасен).
     *
     * @param operation имя операции для идентификации в метриках
     *                  (например: {@code "controller:observability:get"})
     * @param supplier лямбда или метод-ссылка, содержащая бизнес-логику
     * @param <T> тип возвращаемого значения (дженерик для универсальности)
     * @return результат выполнения {@code supplier.get()}
     * @throws RuntimeException если {@code supplier} выбросил исключение
     *         (оно будет перехвачено, записано как ошибка, и проброшено дальше)
     */
    private <T> T timed(final String operation, final Supplier<T> supplier) {
        // Фиксируем время начала выполнения в наносекундах
        final long started = System.nanoTime();
        try {
            // Выполняем бизнес-логику, переданную извне
            final T result = supplier.get();
            
            // Если всё прошло успешно — записываем метрику успеха с длительностью
            observabilityService.recordSuccess(operation, System.nanoTime() - started);
            return result;
            
        } catch (RuntimeException ex) {
            // Если произошло исключение — записываем метрику ошибки
            // Важно: сначала записываем метрику, потом пробрасываем исключение,
            // чтобы Spring мог корректно обработать ошибку и вернуть соответствующий HTTP-статус
            observabilityService.recordFailure(operation, System.nanoTime() - started);
            throw ex;
        }
    }
}