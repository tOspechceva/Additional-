package digital.zil.hl.additional.service;

import digital.zil.hl.additional.model.LessonProgress;
import java.util.Collection;

/**
 * Стратегия расчёта прогресса на основе факта завершения уроков.
 * <p>
 * <b>Логика расчёта:</b>
 * <pre>
 * прогресс = (количество завершённых уроков / общее количество уроков) × 100%
 * </pre>
 * <p>
 * <b>Критерий завершённости:</b> урок считается пройденным, если у записи
 * {@link LessonProgress} заполнено поле {@code completionDate}
 * (проверка делегируется методу {@link LessonProgress#isCompleted()}).
 * <p>
 */
public final class CompletionProgressCalculator implements ProgressCalculator {

    @Override
    public double calculatePercent(final int allLessonsCount, final Collection<LessonProgress> lessonProgresses) {
        // Защита от деления на ноль: если уроков нет, прогресс по определению 0%
        if (allLessonsCount == 0) {
            return 0.0;
        }

        // Подсчитываем количество уроков, у которых isCompleted() == true
        // Stream API позволяет выразительно и эффективно отфильтровать и посчитать элементы
        final long completedLessons = lessonProgresses.stream()
                .filter(LessonProgress::isCompleted) // метод-ссылка как предикат
                .count(); // терминальная операция: возвращает количество элементов

        // Вычисляем процент: умножаем на 100.0 до деления, чтобы получить дробный результат
        // Пример: 3 из 5 уроков → (3 * 100.0) / 5 = 60.0
        return (completedLessons * 100.0) / allLessonsCount;
    }
}