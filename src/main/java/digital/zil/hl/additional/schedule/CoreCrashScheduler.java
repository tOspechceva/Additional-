package digital.zil.hl.additional.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * LAB16: периодически вызывает {@code POST /api/crash} у Core Service.
 * Запрос идёт на {@code CRUD_BASE_URL} (ClusterIP / DNS) — при нескольких подах попадает на случайный экземпляр.
 */
@Component
@ConditionalOnProperty(name = "app.lab16.killer-enabled", havingValue = "true")
public class CoreCrashScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CoreCrashScheduler.class);

    private final RestTemplate restTemplate;
    private final String crashUrl;

    public CoreCrashScheduler(
            final RestTemplate restTemplate,
            @Value("${app.crud.base-url}") final String baseUrl
    ) {
        this.restTemplate = restTemplate;
        final String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.crashUrl = normalized + "/api/crash";
    }

    @Scheduled(fixedDelayString = "${app.lab16.killer-interval-ms:50000}")
    public void triggerCoreCrash() {
        try {
            final ResponseEntity<Void> response = restTemplate.postForEntity(crashUrl, null, Void.class);
            LOG.warn("LAB16 killer: POST {} -> {}", crashUrl, response.getStatusCode());
        } catch (RuntimeException ex) {
            LOG.debug("LAB16 killer: не удалось достучаться до Core (ожидаемо при рестарте): {}", ex.toString());
        }
    }
}
