package digital.zil.hl.additional.config;

import digital.zil.hl.additional.service.CompletionProgressCalculator;
import digital.zil.hl.additional.service.ProgressCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация контекста Spring для модуля Additional service.
 * <p>
 * <b>Назначение:</b> централизованное объявление и настройка бинов, необходимых
 * для работы сервиса расчёта прогресса пользователей.
 * <p>
 * <b>Регистрируемые компоненты:</b>
 * <ul>
 *     <li>{@link ProgressCalculator} — стратегия расчёта процента прогресса
 *         (паттерн Strategy, позволяет в будущем добавить альтернативные алгоритмы);</li>
 *     <li>{@link RestTemplate} — HTTP-клиент с настроенными таймаутами
 *         для надёжного взаимодействия с внешним CRUD-сервисом.</li>
 * </ul>
 * <p>
 * <b>Принципы:</b>
 * <ul>
 *     <li>Явная конфигурация вместо компонентного сканирования — полный контроль над созданием бинов;</li>
 *     <li>Внешняя конфигурация параметров через {@code @Value} — гибкость для разных окружений (dev/stage/prod);</li>
 *     <li>Использование фабричного метода из {@link digital.zil.hl.additional.client.CrudApiClient}
 *         — соблюдение DRY и единого места настройки HTTP-клиента.</li>
 * </ul>
 */
@Configuration
public class ApplicationConfig {

    /**
     * Создаёт и регистрирует в контексте бин {@link ProgressCalculator}.
     * <p>
     * <b>Зачем выносить в конфиг:</b>
     * <ul>
     *     <li>Позволяет в будущем легко подменить реализацию (например, добавить кэширование или асинхронный расчёт);</li>
     *     <li>Упрощает тестирование: в тестах можно заменить бин на mock/spy;</li>
     *     <li>Явно фиксирует выбор стратегии {@link CompletionProgressCalculator} для всего приложения.</li>
     * </ul>
     *
     * @return экземпляр калькулятора прогресса, готовый к внедрению в сервисы
     * @see digital.zil.hl.additional.service.AdditionalProgressService
     */
    @Bean
    public ProgressCalculator progressCalculator() {
        // Текущая реализация считает прогресс как:
        // (количество завершённых уроков / общее количество уроков) * 100
        // В будущем можно добавить:
        // - WeightedProgressCalculator (учёт веса уроков)
        // - TimeDecayProgressCalculator (прогресс с учётом давности)
        return new CompletionProgressCalculator();
    }

    /**
     * Создаёт и настраивает {@link RestTemplate} для HTTP-запросов к CRUD-сервису.
     * <p>
     * <b>Параметры конфигурируются через application.properties:</b>
     * <pre>
     * app.crud.connect-timeout-ms=5000   # ожидание установления TCP-соединения
     * app.crud.read-timeout-ms=30000     # ожидание ответа после отправки запроса
     * </pre>
     * <p>
     * <b>Зачем настраивать таймауты:</b>
     * <ul>
     *     <li>Защита от "зависших" запросов: если CRUD-сервис не отвечает, поток не будет заблокирован бесконечно;</li>
     *     <li>Быстрое обнаружение проблем: при недоступности сервиса запрос упадёт с понятной ошибкой;</li>
     *     <li>Предсказуемое поведение под нагрузкой: таймауты помогают избежать каскадных сбоев.</li>
     * </ul>
     *
     * @param connectTimeoutMs таймаут подключения в мс, инжектируется из конфигурации
     * @param readTimeoutMs таймаут чтения в мс, инжектируется из конфигурации
     * @return настроенный {@link RestTemplate}, готовый к внедрению в {@link digital.zil.hl.additional.client.CrudApiClient}
     * @see digital.zil.hl.additional.client.CrudApiClient#buildRestTemplate(int, int)
     */
    @Bean
    public RestTemplate restTemplate(
            @Value("${app.crud.connect-timeout-ms}") final int connectTimeoutMs,
            @Value("${app.crud.read-timeout-ms}") final int readTimeoutMs
    ) {
        // Делегируем создание клиента фабричному методу в CrudApiClient.
        // Это обеспечивает:
        // 1) Единое место настройки HTTP-клиента (принцип Single Responsibility)
        // 2) Возможность повторного использования логики в тестах
        // 3) Чистоту конфигурации: ApplicationConfig отвечает только за "что", а не "как"
        return digital.zil.hl.additional.client.CrudApiClient.buildRestTemplate(connectTimeoutMs, readTimeoutMs);
    }
}