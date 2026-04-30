package digital.zil.hl.additional.model;

/**
 * Пользователь платформы.
 * <p>
 * Данная модель используется Additional service как "проекция" ответов CRUD service
 * (id/login/email), поэтому она не содержит лишней доменной информации.
 *
 * @param id идентификатор пользователя
 * @param login логин
 * @param email email
 */
public record User(long id, String login, String email) {
}
