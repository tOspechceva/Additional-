package digital.zil.hl.additional.client;

import digital.zil.hl.additional.model.LessonProgress;
import digital.zil.hl.additional.model.User;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Клиент для вызова основного CRUD service.
 */
@Component
public class CrudApiClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public CrudApiClient(
            final RestTemplate restTemplate,
            @Value("${app.crud.base-url}") final String baseUrl
    ) {
        this.restTemplate = Objects.requireNonNull(restTemplate);
        this.baseUrl = Objects.requireNonNull(baseUrl);
    }

    public List<User> getAllUsers() {
        final List<CrudUserResponse> users = restTemplate.exchange(
                baseUrl + "/api/users",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<CrudUserResponse>>() {
                }
        ).getBody();
        if (users == null) {
            throw new IllegalStateException("CRUD /api/users вернул пустое тело");
        }
        return users.stream()
                .map(user -> new User(user.id(), user.login(), user.email()))
                .toList();
    }

    public int getLessonsCount() {
        final List<CrudLessonResponse> lessons = restTemplate.exchange(
                baseUrl + "/api/lessons",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<CrudLessonResponse>>() {
                }
        ).getBody();
        if (lessons == null) {
            throw new IllegalStateException("CRUD /api/lessons вернул пустое тело");
        }
        return lessons.size();
    }

    public List<LessonProgress> getAllProgressEntries() {
        final List<CrudLessonProgressResponse> progress = restTemplate.exchange(
                baseUrl + "/api/progress",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<CrudLessonProgressResponse>>() {
                }
        ).getBody();
        if (progress == null) {
            throw new IllegalStateException("CRUD /api/progress вернул пустое тело");
        }
        return progress.stream()
                .map(item -> new LessonProgress(item.userId(), item.lessonId(), item.completionDate(), item.testResult()))
                .toList();
    }

    public static RestTemplate buildRestTemplate(final int connectTimeoutMs, final int readTimeoutMs) {
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(requestFactory);
    }

    private record CrudUserResponse(long id, String login, String email) {
    }

    private record CrudLessonResponse(long id, String topic, int videoDurationMinutes, String testName, int maxTestScore) {
    }

    private record CrudLessonProgressResponse(long userId, long lessonId, java.time.LocalDate completionDate, int testResult) {
    }
}
