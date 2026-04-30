package digital.zil.hl.additional.client.dto;

import java.time.LocalDate;

/**
 * JSON-модель ответа CRUD: {@code GET /api/progress}.
 */
public record CrudLessonProgressResponse(
        long userId,
        long lessonId,
        LocalDate completionDate,
        int testResult
) {
}
