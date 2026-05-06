package digital.zil.hl.additional.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LAB16: оборачивает HTTP-вызовы к Core в Retry или CircuitBreaker.
 */
@Component
public class Lab16ResilienceExecutor {

    private static final String CRUD = "crud";

    private final Lab16Mode mode;
    private final RetryRegistry retryRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public Lab16ResilienceExecutor(
            @Value("${app.lab16.mode:NONE}") final String modeRaw,
            final RetryRegistry retryRegistry,
            final CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        this.mode = Lab16Mode.fromProperty(modeRaw);
        this.retryRegistry = retryRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public <T> T execute(final Supplier<T> supplier) {
        return switch (mode) {
            case NONE -> supplier.get();
            case RETRY -> Retry.decorateSupplier(retryRegistry.retry(CRUD), supplier).get();
            case CIRCUIT_BREAKER ->
                    CircuitBreaker.decorateSupplier(circuitBreakerRegistry.circuitBreaker(CRUD), supplier).get();
        };
    }
}
