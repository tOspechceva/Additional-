package digital.zil.hl.additional.service;

import digital.zil.hl.additional.client.CrudApiClient;
import digital.zil.hl.additional.model.LessonProgress;
import digital.zil.hl.additional.model.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Сервис расчета прогресса, получающий данные из CRUD service через API.
 */
@Service
public class AdditionalProgressService {
    private final CrudApiClient crudApiClient;
    private final ProgressCalculator progressCalculator;

    public AdditionalProgressService(
            final CrudApiClient crudApiClient,
            final ProgressCalculator progressCalculator
    ) {
        this.crudApiClient = Objects.requireNonNull(crudApiClient);
        this.progressCalculator = Objects.requireNonNull(progressCalculator);
    }

    public double calculateUserProgressPercent(final long userId) {
        final List<User> users = crudApiClient.getAllUsers();
        users.stream()
                .filter(user -> user.id() == userId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));

        final int allLessonsCount = crudApiClient.getLessonsCount();
        final List<LessonProgress> userProgress = crudApiClient.getAllProgressEntries().stream()
                .filter(progress -> progress.userId() == userId)
                .toList();
        return progressCalculator.calculatePercent(allLessonsCount, userProgress);
    }

    public Optional<UserProgressView> getUserProgress(final long userId) {
        return calculateAllUsersProgressPercent().entrySet().stream()
                .filter(entry -> entry.getKey().id() == userId)
                .findFirst()
                .map(entry -> new UserProgressView(entry.getKey(), entry.getValue()));
    }

    public Map<User, Double> calculateAllUsersProgressPercent() {
        final List<User> users = crudApiClient.getAllUsers();
        final int allLessonsCount = crudApiClient.getLessonsCount();
        final List<LessonProgress> allProgress = crudApiClient.getAllProgressEntries();

        final Map<User, Double> progressByUser = new LinkedHashMap<>();
        for (final User user : users) {
            final List<LessonProgress> userProgress = allProgress.stream()
                    .filter(progress -> progress.userId() == user.id())
                    .toList();
            final double progressPercent = progressCalculator.calculatePercent(allLessonsCount, userProgress);
            progressByUser.put(user, progressPercent);
        }
        return progressByUser;
    }

    public record UserProgressView(User user, double progressPercent) {
    }
}
