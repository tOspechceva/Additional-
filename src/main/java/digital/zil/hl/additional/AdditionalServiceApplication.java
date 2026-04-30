package digital.zil.hl.additional;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа Additional service.
 */
@SpringBootApplication
@EnableScheduling
public class AdditionalServiceApplication {
    public static void main(final String[] args) {
        SpringApplication.run(AdditionalServiceApplication.class, args);
    }
}
