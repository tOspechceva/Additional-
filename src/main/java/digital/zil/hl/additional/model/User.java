package digital.zil.hl.additional.model;

/**
 * Пользователь платформы.
 *
 * @param id идентификатор пользователя
 * @param login логин
 * @param email email
 */
public record User(long id, String login, String email) {
}
