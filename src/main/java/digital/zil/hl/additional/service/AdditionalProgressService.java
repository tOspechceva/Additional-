package digital.zil.hl.additional.service;

import digital.zil.hl.additional.client.CrudApiClient;
import digital.zil.hl.additional.client.CrudResponseMapper;
import digital.zil.hl.additional.model.LessonProgress;
import digital.zil.hl.additional.model.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * Бизнес-сервис для расчёта прогресса прохождения курса в процентах.
 */
@Service
public class AdditionalProgressService {

    /** Клиент для вызовов внешнего CRUD-сервиса. */
    private final CrudApiClient crudApiClient;

    private final CrudResponseMapper crudResponseMapper;

    /** Стратегия расчёта процента прогресса (вынесена для тестируемости и расширяемости). */
    private final ProgressCalculator progressCalculator;
    private final ObservabilityService observabilityService;

    /**
     * Конструктор с обязательными зависимостями (Constructor Injection).
     *
     * @param crudApiClient клиент для получения данных из CRUD-сервиса
     * @param progressCalculator стратегия расчёта процента прогресса
     * @throws NullPointerException если любая из зависимостей {@code null}
     */
    public AdditionalProgressService(
            final CrudApiClient crudApiClient,
            final CrudResponseMapper crudResponseMapper,
            final ProgressCalculator progressCalculator,
            final ObservabilityService observabilityService
    ) {
        // Defensive programming: лучше упасть сразу при создании бина,
        // чем получить NPE в рантайме при обработке запроса
        this.crudApiClient = Objects.requireNonNull(crudApiClient, "crudApiClient не может быть null");
        this.crudResponseMapper = Objects.requireNonNull(crudResponseMapper, "crudResponseMapper не может быть null");
        this.progressCalculator = Objects.requireNonNull(progressCalculator, "progressCalculator не может быть null");
        this.observabilityService = Objects.requireNonNull(observabilityService, "observabilityService не может быть null");
    }

    /**
     * Рассчитывает процент прогресса для конкретного пользователя.
     * <p>
     * <b>Алгоритм работы:</b>
     * <ol>
     *     <li>Загружаем всех пользователей из CRUD-сервиса;</li>
     *     <li>Проверяем, что пользователь с указанным {@code userId} существует
     *         (если нет — выбрасываем {@link IllegalArgumentException});</li>
     *     <li>Получаем общее количество уроков (знаменатель для расчёта процента);</li>
     *     <li>Загружаем все записи прогресса и фильтруем только по целевому пользователю;</li>
     *     <li>Делегируем расчёт процента стратегии {@link ProgressCalculator}.</li>
     * </ol>
     *
     * @param userId идентификатор пользователя
     * @return процент прогресса (от 0.0 до 100.0)
     * @throws IllegalArgumentException если пользователь с таким ID не найден
     * @throws org.springframework.web.client.RestClientException при ошибках сети или недоступности CRUD-сервиса
     */
    public double calculateUserProgressPercent(final long userId) {
        return timedCalc("calc:additional:user-progress", () -> {
            final List<User> users = crudResponseMapper.toUsers(crudApiClient.getUsersBody());
            users.stream()
                    .filter(user -> user.id() == userId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));
            final int allLessonsCount = crudResponseMapper.toLessonsCount(crudApiClient.getLessonsBody());
            final List<LessonProgress> userProgress = crudResponseMapper.toProgressEntries(crudApiClient.getProgressBody()).stream()
                    .filter(progress -> progress.userId() == userId)
                    .toList();
            return progressCalculator.calculatePercent(allLessonsCount, userProgress);
        });
    }

    /**
     * Возвращает прогресс пользователя в виде {@link UserProgressView}, если пользователь найден.
     * <p>
     * <b>Зачем этот метод:</b> удобная обёртка для контроллера, когда "пользователь не найден" —
     * не ошибка, а нормальная ситуация (возвращаем {@code 404 Not Found} вместо {@code 500}).
     * <p>
     *
     * @param userId идентификатор пользователя
     * @return {@link Optional#of(UserProgressView)} если пользователь найден, иначе {@link Optional#empty()}
     */
    public Optional<UserProgressView> getUserProgress(final long userId) {
        // Реиспользуем метод расчёта для всех, затем фильтруем результат
        // Это дублирует работу, но упрощает код для учебного проекта
        return calculateAllUsersProgressPercent().entrySet().stream()
                .filter(entry -> entry.getKey().id() == userId)
                .findFirst()
                .map(entry -> new UserProgressView(entry.getKey(), entry.getValue()));
    }

    /**
     * Рассчитывает прогресс в процентах для всех пользователей системы.
     * <p>
     * <b>Алгоритм:</b>
     * <ol>
     *     <li>Загружаем список всех пользователей;</li>
     *     <li>Получаем общее количество уроков (единое для всех);</li>
     *     <li>Загружаем все записи прогресса;</li>
     *     <li>Для каждого пользователя:</li>
     *     <ul>
     *         <li>фильтруем прогресс по его userId;</li>
     *         <li>вычисляем процент через {@link ProgressCalculator};</li>
     *         <li>сохраняем в результат.</li>
     *     </ul>
     * </ol>
     * <p>
     *
     * @return {@link Map} где ключ — пользователь, значение — его прогресс в процентах
     */
    public Map<User, Double> calculateAllUsersProgressPercent() {
        return timedCalc("calc:additional:all-users-progress", () -> {
            final List<User> users = crudResponseMapper.toUsers(crudApiClient.getUsersBody());
            final int allLessonsCount = crudResponseMapper.toLessonsCount(crudApiClient.getLessonsBody());
            final List<LessonProgress> allProgress = crudResponseMapper.toProgressEntries(crudApiClient.getProgressBody());
            final Map<User, Double> progressByUser = new LinkedHashMap<>();
            for (final User user : users) {
                final List<LessonProgress> userProgress = allProgress.stream()
                        .filter(progress -> progress.userId() == user.id())
                        .toList();
                final double progressPercent = progressCalculator.calculatePercent(allLessonsCount, userProgress);
                progressByUser.put(user, progressPercent);
            }
            return progressByUser;
        });
    }

    private <T> T timedCalc(final String operation, final Supplier<T> supplier) {
        final long started = System.nanoTime();
        try {
            final T result = supplier.get();
            observabilityService.recordSuccess(operation, System.nanoTime() - started);
            return result;
        } catch (RuntimeException ex) {
            observabilityService.recordFailure(operation, System.nanoTime() - started);
            throw ex;
        }
    }


    public record UserProgressView(User user, double progressPercent) {
        // Record автоматически реализует:
        // - конструктор с параметрами
        // - геттеры user() и progressPercent()
        // - equals/hashCode по всем полям
        // - toString в формате UserProgressView[user=..., progressPercent=...]
    }
}