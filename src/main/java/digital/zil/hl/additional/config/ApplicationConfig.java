package digital.zil.hl.additional.config;

import digital.zil.hl.additional.service.CompletionProgressCalculator;
import digital.zil.hl.additional.service.ProgressCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация бинов Additional service.
 */
@Configuration
public class ApplicationConfig {

    @Bean
    public ProgressCalculator progressCalculator() {
        return new CompletionProgressCalculator();
    }

    @Bean
    public RestTemplate restTemplate(
            @Value("${app.crud.connect-timeout-ms}") final int connectTimeoutMs,
            @Value("${app.crud.read-timeout-ms}") final int readTimeoutMs
    ) {
        return digital.zil.hl.additional.client.CrudApiClient.buildRestTemplate(connectTimeoutMs, readTimeoutMs);
    }
}
