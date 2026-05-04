package digital.zil.hl.additional.client;

import digital.zil.hl.additional.client.dto.CrudLessonProgressResponse;
import digital.zil.hl.additional.client.dto.CrudLessonResponse;
import digital.zil.hl.additional.client.dto.CrudUserResponse;
import digital.zil.hl.additional.model.LessonProgress;
import digital.zil.hl.additional.model.User;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Преобразование тел ответов CRUD API во внутренние доменные модели.
 */
@Component
public class CrudResponseMapper {

    public User toUser(final CrudUserResponse user) {
        Objects.requireNonNull(user, "CRUD user body не может быть null");
        return new User(user.id(), user.login(), user.email());
    }

    public List<User> toUsers(final List<CrudUserResponse> body) {
        requireNonEmptyBody(body, "CRUD /api/users вернул пустое тело (null)");
        return body.stream()
                .map(this::toUser)
                .toList();
    }

    public int toLessonsCount(final List<CrudLessonResponse> body) {
        requireNonEmptyBody(body, "CRUD /api/lessons вернул пустое тело (null)");
        return body.size();
    }

    public List<LessonProgress> toProgressEntries(final List<CrudLessonProgressResponse> body) {
        requireNonEmptyBody(body, "CRUD /api/progress вернул пустое тело (null)");
        return body.stream()
                .map(item -> new LessonProgress(
                        item.userId(),
                        item.lessonId(),
                        item.completionDate(),
                        item.testResult()
                ))
                .toList();
    }

    private static <T> void requireNonEmptyBody(final T body, final String message) {
        if (body == null) {
            throw new IllegalStateException(message);
        }
    }
}
