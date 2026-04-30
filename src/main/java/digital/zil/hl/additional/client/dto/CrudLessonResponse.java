package digital.zil.hl.additional.client.dto;

/**
 * JSON-модель ответа CRUD: {@code GET /api/lessons}.
 */
public record CrudLessonResponse(
        long id,
        String topic,
        int videoDurationMinutes,
        String testName,
        int maxTestScore
) {
}
