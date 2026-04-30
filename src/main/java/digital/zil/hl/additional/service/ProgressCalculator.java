package digital.zil.hl.additional.service;

import digital.zil.hl.additional.model.LessonProgress;
import java.util.Collection;


@FunctionalInterface
public interface ProgressCalculator {

    /**
     * Вычисляет процент завершения курса для заданного набора данных.
     * <p>
     * <b>Параметры метода:</b>
     * <ul>
     *     <li>{@code allLessonsCount} — общее количество уроков в курсе.
     *         Используется как знаменатель при расчёте процента.
     *         Может быть {@code 0} (пустой курс) — реализация должна обработать этот случай;</li>
     *     <li>{@code lessonProgresses} — коллекция записей прогресса конкретного пользователя.
     *         Может быть пустой, если пользователь ещё не начал курс.
     *         Не должна быть {@code null} (контракт метода).</li>
     * </ul>
     * <p>
     * <b>Возвращаемое значение:</b>
     * <ul>
     *     <li>{@code double} в диапазоне {@code [0.0; 100.0]};</li>
     *     <li>{@code 0.0} — если ни один урок не завершён или курс пуст;</li>
     *     <li>{@code 100.0} — если все уроки завершены;</li>
     *     <li>дробное значение — при частичном завершении (например, {@code 66.67}).</li>
     * </ul>
     */
    double calculatePercent(int allLessonsCount, Collection<LessonProgress> lessonProgresses);
}