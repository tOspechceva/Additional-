package digital.zil.hl.additional.client;

import digital.zil.hl.additional.client.dto.CrudLessonProgressResponse;
import digital.zil.hl.additional.client.dto.CrudLessonResponse;
import digital.zil.hl.additional.client.dto.CrudUserResponse;
import digital.zil.hl.additional.service.ObservabilityService;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Только HTTP-вызовы к CRUD API (тело ответа без маппинга в домен).
 */
@Component
public class CrudApiClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObservabilityService observabilityService;

    public CrudApiClient(
            final RestTemplate restTemplate,
            final ObservabilityService observabilityService,
            @Value("${app.crud.base-url}") final String baseUrl
    ) {
        this.restTemplate = Objects.requireNonNull(restTemplate, "RestTemplate не может быть null");
        this.observabilityService = Objects.requireNonNull(observabilityService, "observabilityService не может быть null");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl не может быть null");
    }

    public List<CrudUserResponse> getUsersBody() {
        return timedS2s("s2s:crud:get-users", () -> restTemplate.exchange(
                baseUrl + "/api/users",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<CrudUserResponse>>() {
                }
        ).getBody());
    }

    public List<CrudLessonResponse> getLessonsBody() {
        return timedS2s("s2s:crud:get-lessons", () -> restTemplate.exchange(
                baseUrl + "/api/lessons",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<CrudLessonResponse>>() {
                }
        ).getBody());
    }

    public List<CrudLessonProgressResponse> getProgressBody() {
        return timedS2s("s2s:crud:get-progress", () -> restTemplate.exchange(
                baseUrl + "/api/progress",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<CrudLessonProgressResponse>>() {
                }
        ).getBody());
    }

    public CrudUserResponse getUserByIdBody(final long userId) {
        return timedS2s("s2s:crud:get-user-by-id", () -> restTemplate.exchange(
                baseUrl + "/api/users/" + userId,
                HttpMethod.GET,
                null,
                CrudUserResponse.class
        ).getBody());
    }

    public static RestTemplate buildRestTemplate(final int connectTimeoutMs, final int readTimeoutMs) {
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(requestFactory);
    }

    private <T> T timedS2s(final String operation, final Supplier<T> supplier) {
        final long started = System.nanoTime();
        try {
            final T result = supplier.get();
            observabilityService.recordSuccess(operation, System.nanoTime() - started);
            return result;
        } catch (RuntimeException ex) {
            observabilityService.recordFailure(operation, System.nanoTime() - started);
            throw ex;
        }
    }
}
