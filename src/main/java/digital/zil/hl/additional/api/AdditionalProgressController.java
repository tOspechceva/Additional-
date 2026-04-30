package digital.zil.hl.additional.api;

import digital.zil.hl.additional.api.dto.UserProgressResponse;
import digital.zil.hl.additional.api.dto.NPlusOneProgressResponse;
import digital.zil.hl.additional.model.User;
import digital.zil.hl.additional.service.AdditionalProgressService;
import digital.zil.hl.additional.service.ObservabilityService;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер для предоставления данных о прогрессе пользователей в разделе "Дополнительно".
 * <p>
 * Реализует принцип "тонкого контроллера": не содержит бизнес-логики,
 * а делегирует все вычисления слою сервиса ({@link AdditionalProgressService}).
 * Отвечает только за приём HTTP-запросов, маппинг параметров и формирование JSON-ответа.
 */
@RestController
@RequestMapping("/api/progress")
public class AdditionalProgressController {

    /** Сервис бизнес-логики. Внедряется через конструктор (Constructor Injection) для тестопригодности и неизменяемости. */
    private final AdditionalProgressService additionalProgressService;
    private final ObservabilityService observabilityService;

    /**
     * Конструктор с обязательной зависимостью.
     * <ul>
     *   <li>{@code final} гарантирует, что ссылка на сервис не будет переприсвоена</li>
     *   <li>Constructor Injection позволяет запускать модульные тесты без Spring-контекста</li>
     *   <li>Spring автоматически разрешает зависимость при старте приложения</li>
     * </ul>
     */
    public AdditionalProgressController(
            final AdditionalProgressService additionalProgressService,
            final ObservabilityService observabilityService
    ) {
        this.additionalProgressService = additionalProgressService;
        this.observabilityService = observabilityService;
    }

    /**
     * Возвращает прогресс конкретного пользователя в процентах.
     *
     * @param userId идентификатор пользователя (путь URL: {@code /api/progress/users/{userId}})
     * @return DTO с публичными данными пользователя и рассчитанным прогрессом
     * @throws IllegalArgumentException если пользователь с указанным ID отсутствует в системе
     */
    @GetMapping("/users/{userId}")
    public UserProgressResponse getUserProgress(@PathVariable final long userId) {
        return timed("controller:getUserProgress", () -> {
            final AdditionalProgressService.UserProgressView userProgressView = additionalProgressService.getUserProgress(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));

            final User user = userProgressView.user();
            return new UserProgressResponse(
                    user.id(),
                    user.login(),
                    user.email(),
                    userProgressView.progressPercent()
            );
        });
    }

    /**
     * Возвращает прогресс всех пользователей системы.
     * <p>
     * Endpoint оптимизирован для отображения в административных панелях и дашбордах.
     * Порядок элементов в ответе соответствует порядку, возвращаемому сервисом.
     *
     * @return список DTO с данными пользователей и их процентом прогресса
     */
    @GetMapping("/users")
    public List<UserProgressResponse> getAllUsersProgress() {
        return timed("controller:getAllUsersProgress", () -> {
            final Map<User, Double> allProgress = additionalProgressService.calculateAllUsersProgressPercent();
            return allProgress.entrySet().stream()
                    .map(entry -> new UserProgressResponse(
                            entry.getKey().id(),
                            entry.getKey().login(),
                            entry.getKey().email(),
                            entry.getValue()
                    ))
                    .toList();
        });
    }

    /**
     * Демонстрационный endpoint с намеренно "плохой" N+1 логикой.
     * <p>
     * Для каждой строки из общего прогресса отдельно запрашивает:
     * <ul>
     *   <li>пользователя по userId</li>
     *   <li>урок по lessonId</li>
     * </ul>
     */
    @GetMapping("/n-plus-one")
    public List<NPlusOneProgressResponse> getAllProgressWithNPlusOneLookups() {
        return timed("controller:getAllProgressWithNPlusOneLookups", () -> additionalProgressService
                .calculateProgressWithNPlusOneLookups()
                .stream()
                .map(view -> new NPlusOneProgressResponse(
                        view.user().id(),
                        view.user().login(),
                        view.progress().lessonId(),
                        view.lessonTopic(),
                        view.progress().completionDate(),
                        view.progress().testResult()
                ))
                .toList());
    }

    private <T> T timed(final String operation, final Supplier<T> supplier) {
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
}
