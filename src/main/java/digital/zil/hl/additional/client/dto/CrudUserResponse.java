package digital.zil.hl.additional.client.dto;

/**
 * JSON-модель ответа CRUD: {@code GET /api/users}.
 */
public record CrudUserResponse(long id, String login, String email) {
}
