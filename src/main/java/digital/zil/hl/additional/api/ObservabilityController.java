package digital.zil.hl.additional.api;

import digital.zil.hl.additional.service.ObservabilityService;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Выдача агрегированной observability-статистики (LAB9).
 */
@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {
    private final ObservabilityService observabilityService;

    public ObservabilityController(final ObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    @GetMapping
    public Object getStats(@RequestParam(required = false) final String window) {
        return timed("controller:observability:get", () -> {
            if (window == null || window.isBlank()) {
                return observabilityService.getAllWindows();
            }
            return observabilityService.getWindow(window.trim().toLowerCase());
        });
    }

    @GetMapping("/windows")
    public Map<String, Map<String, ObservabilityService.OperationStats>> getAllWindows() {
        return timed("controller:observability:getAllWindows", observabilityService::getAllWindows);
    }

    private <T> T timed(final String operation, final Supplier<T> supplier) {
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
