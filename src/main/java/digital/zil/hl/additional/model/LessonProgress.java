package digital.zil.hl.additional.model;

import java.time.LocalDate;

/**
 * Прогресс прохождения урока пользователем.
 *
 * @param userId идентификатор пользователя
 * @param lessonId идентификатор урока
 * @param completionDate дата завершения урока
 * @param testResult результат теста
 */
public record LessonProgress(long userId, long lessonId, LocalDate completionDate, int testResult) {
    public boolean isCompleted() {
        return completionDate != null;
    }
}
