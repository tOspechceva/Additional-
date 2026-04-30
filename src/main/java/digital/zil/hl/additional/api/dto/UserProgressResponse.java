package digital.zil.hl.additional.api.dto;

/**
 * Ответ с прогрессом пользователя в процентах.
 */
public record UserProgressResponse(long userId, String login, String email, double progressPercent) {
}
