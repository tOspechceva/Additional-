package digital.zil.hl.additional.service;

import digital.zil.hl.additional.model.LessonProgress;
import java.util.Collection;

/**
 * Процент завершенных уроков от общего количества.
 */
public final class CompletionProgressCalculator implements ProgressCalculator {
    @Override
    public double calculatePercent(final int allLessonsCount, final Collection<LessonProgress> lessonProgresses) {
        if (allLessonsCount == 0) {
            return 0.0;
        }

        final long completedLessons = lessonProgresses.stream()
                .filter(LessonProgress::isCompleted)
                .count();

        return (completedLessons * 100.0) / allLessonsCount;
    }
}
