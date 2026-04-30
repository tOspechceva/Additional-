package digital.zil.hl.additional.api.dto;

import java.time.LocalDate;

/**
 * Ответ для демонстрации N+1 логики:
 * на каждую запись прогресса выполняются отдельные S2S вызовы за пользователем и уроком.
 */
public record NPlusOneProgressResponse(
        long userId,
        String userLogin,
        long lessonId,
        String lessonTopic,
        LocalDate completionDate,
        int testResult
) {
}
