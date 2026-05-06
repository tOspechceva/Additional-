package digital.zil.hl.additional.health;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Проверка доступности кластера Kafka (LAB15).
 */
@Component("kafka")
public class KafkaClusterHealthIndicator implements HealthIndicator {

    private final String bootstrapServers;

    public KafkaClusterHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers}") final String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public Health health() {
        final Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000");
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "3000");
        try (AdminClient admin = AdminClient.create(props)) {
            final String clusterId = admin.describeCluster().clusterId().get(3, TimeUnit.SECONDS);
            return Health.up()
                    .withDetails(Map.of("bootstrapServers", bootstrapServers, "clusterId", clusterId))
                    .build();
        } catch (Exception ex) {
            return Health.down()
                    .withException(ex)
                    .withDetail("bootstrapServers", bootstrapServers)
                    .build();
        }
    }
}
