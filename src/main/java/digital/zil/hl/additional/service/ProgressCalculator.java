package digital.zil.hl.additional.service;

import digital.zil.hl.additional.model.LessonProgress;
import java.util.Collection;

/**
 * Стратегия расчета процента завершения курса.
 */
public interface ProgressCalculator {
    double calculatePercent(int allLessonsCount, Collection<LessonProgress> lessonProgresses);
}
