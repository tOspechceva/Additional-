package digital.zil.hl.additional.api.dto;

/**
 * Ответ с прогрессом пользователя.
 */
public record UserProgressResponse(long userId, String login, String email, double progressPercent) {
}
